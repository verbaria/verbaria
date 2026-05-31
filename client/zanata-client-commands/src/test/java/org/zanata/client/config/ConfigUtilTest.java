package org.zanata.client.config;

import java.net.URL;

import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.configuration2.io.FileHandler;
import org.junit.jupiter.api.Test;
import org.zanata.client.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigUtilTest {
    @Test
    public void testReadUser() throws Exception {
        INIConfiguration config = new INIConfiguration();
        new FileHandler(config).load(
                TestUtils.loadFromClasspath("verbaria.ini"));
        SubnodeConfiguration servers = config.getSection("servers");
        String url = "https://translate.jboss.org/";
        String username = "joe";
        String key = "1234";
        String prefix = ConfigUtil.findPrefix(servers, new URL(url));

        String gotURL = servers.getString(prefix + ".url");
        String gotUsername = servers.getString(prefix + ".username");
        String gotKey = servers.getString(prefix + ".key");

        assertEquals(url, gotURL);
        assertEquals(username, gotUsername);
        assertEquals(key, gotKey);
    }

}
