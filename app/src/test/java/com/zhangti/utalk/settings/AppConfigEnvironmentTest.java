package com.zhangti.utalk.settings;

import com.zhangti.utalk.AppConfig;
import com.zhangti.utalk.agent.tool.mcp.McpServerConfig;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AppConfigEnvironmentTest {
    @Test
    public void environmentOnlyChangesDiDiEndpoint() {
        AppConfig sandbox = config(DiDiEnvironment.SANDBOX);
        AppConfig production = config(DiDiEnvironment.PRODUCTION);
        assertEquals("https://mcp.didichuxing.com/mcp-servers-sandbox?key=ride", url(sandbox, "DiDi-Ride"));
        assertEquals("https://mcp.didichuxing.com/mcp-servers?key=ride", url(production, "DiDi-Ride"));
        assertEquals(url(sandbox, "amap-maps"), url(production, "amap-maps"));
    }

    private AppConfig config(DiDiEnvironment mode) {
        return new AppConfig("model", "voice", "map", "flight", "weather", "hotel", "ride", mode);
    }

    private String url(AppConfig config, String name) {
        for (McpServerConfig server : config.getRemoteMcpServers()) {
            if (name.equals(server.getName())) return server.getUrl();
        }
        throw new AssertionError("Missing MCP server " + name);
    }
}
