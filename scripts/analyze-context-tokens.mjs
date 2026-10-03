#!/usr/bin/env node
// Read-only aggregate analysis. Never prints conversations, coordinates, keys or order IDs.
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const db = process.argv[2];
if (!db) throw new Error('Usage: node scripts/analyze-context-tokens.mjs DATABASE');
const query = sql => JSON.parse(execFileSync('sqlite3', ['-json', db, sql], { encoding: 'utf8' }) || '[]');
export function textTokens(value = '') {
  let units = 0;
  for (const c of value) units += c.codePointAt(0) <= 127 ? .4 : /\p{Script=Han}/u.test(c) ? .8 : 1;
  return Math.ceil(units * 1.15);
}
const stats = values => {
  const s = [...values].sort((a, b) => a - b);
  const percentile = p => s[Math.max(0, Math.ceil(s.length * p) - 1)];
  return s.length ? { n:s.length, min:s[0], median:percentile(.5), p90:percentile(.9), mean:Math.round(s.reduce((a,b)=>a+b,0)/s.length), max:s.at(-1) } : null;
};
const requests = query('SELECT id,payload,projection,usage FROM model_requests ORDER BY id');
const latest = JSON.parse(requests.at(-1).payload);
const core = new Map(latest.tools.map(t => [t.function.name, t.function]));
const definitions = new Map(core);
const sources = [['amap-maps','amap'],['VariFlight-Aviation','flight'],['DIDA-Hotel','hotel'],['DiDi-Ride','ride'],['Caiyun-Weather','weather']];
for (const [file, prefix] of sources) {
  const markdown = readFileSync(`${root}doc/mcp-tools/${file}.md`, 'utf8');
  for (const match of markdown.matchAll(/^## (\S+)\n([\s\S]*?)(?=^## |$(?![\s\S]))/gm)) {
    const [, name, body] = match;
    if (prefix === 'ride' && (!name.startsWith('taxi_') || name === 'taxi_generate_ride_app_link')) continue;
    if (name === 'maps_schema_take_taxi') continue;
    const fullName = `${prefix}__${name}`;
    if (definitions.has(fullName)) continue;
    const description = body.match(/描述: ([\s\S]*?)\n\n输入 Schema:/)?.[1];
    const schema = body.match(/```json\n([\s\S]*?)\n```/)?.[1];
    if (!description || !schema) throw new Error(`Cannot parse definition ${fullName}`);
    definitions.set(fullName, {name:fullName, description, parameters:JSON.parse(schema)});
  }
}
const definitionCost = t => textTokens(t.name)+textTokens(t.description)+textTokens(JSON.stringify(t.parameters))+24;
const events = query('SELECT id,session_id,turn_no,kind,content,tool_calls,tool_call_id,tool_name,is_error FROM events ORDER BY id');
const calls = new Map();
let unmatched = 0;
for (let i=0;i<events.length;i++) {
  const e = events[i];
  const parsed = JSON.parse(e.tool_calls || '[]');
  if (!parsed.length) continue;
  let end = i+1;
  while (end<events.length && events[end].session_id===e.session_id && events[end].kind!=='assistant') end++;
  const candidates = events.slice(i+1,end).filter(x=>x.kind==='tool_result');
  for (const c of parsed) {
    const index = candidates.findIndex(x=>x.tool_call_id===c.id && x.tool_name===c.name);
    if (index<0) { unmatched++; continue; }
    const r=candidates.splice(index,1)[0];
    const tokens=textTokens(c.name)+textTokens(c.id)+textTokens(c.arguments)+16+textTokens(r.content)+textTokens(c.id)+24;
    if (!calls.has(c.name)) calls.set(c.name,[]);
    calls.get(c.name).push({ tokens, resultTokens:textTokens(r.content), chars:[...(r.content||'')].length, error:!!r.is_error });
  }
}
const turns = new Map();
for (const e of events.filter(e=>e.turn_no>0)) {
  const key=`${e.session_id}:${e.turn_no}`;
  if (!turns.has(key)) turns.set(key,{dialogue:0,dialogueText:0,calls:0,images:0});
  const t=turns.get(key);
  if (e.kind==='user'||e.kind==='assistant') {
    t.dialogueText+=textTokens(e.content||'');
    t.dialogue+=textTokens(e.content||'')+12;
  }
  if (e.kind==='tool_result') t.calls++;
  if (e.kind==='image') t.images++;
}
console.log(JSON.stringify({
  definitionSource:'Latest captured request for active definitions; repository MCP snapshots for inactive definitions',
  definitionTotals:{core: [...core.values()].reduce((a,t)=>a+definitionCost(t),0),all:[...definitions.values()].reduce((a,t)=>a+definitionCost(t),0)},
  definitions:[...definitions.values()].map(t=>({name:t.name,core:core.has(t.name),tokens:definitionCost(t)})).sort((a,b)=>a.name.localeCompare(b.name)),
  tools:[...calls].map(([name,s])=>({name,errors:s.filter(x=>x.error).length,all:stats(s.map(x=>x.tokens)),success:stats(s.filter(x=>!x.error).map(x=>x.tokens)),result:stats(s.map(x=>x.resultTokens)),resultChars:stats(s.map(x=>x.chars))})).sort((a,b)=>a.name.localeCompare(b.name)),
  sample:{toolResults:events.filter(e=>e.kind==='tool_result').length,pairedCalls:[...calls.values()].reduce((a,s)=>a+s.length,0),unmatchedCalls:unmatched,userTurns:events.filter(e=>e.kind==='user').length,images:events.filter(e=>e.kind==='image').length,dialoguePerTurn:stats([...turns.values()].map(t=>t.dialogue)),dialogueTextPerTurn:stats([...turns.values()].map(t=>t.dialogueText)),callsPerTurn:stats([...turns.values()].map(t=>t.calls))},
  measuredRequests:requests.filter(r=>r.usage).map(r=>({id:r.id,usage:JSON.parse(r.usage),budget:JSON.parse(r.projection).token_budget})),
},null,2));
