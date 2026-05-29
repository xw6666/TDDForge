package com.tddforge;

import com.tddforge.config.ConfigValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "repo.path=/tmp",
    "repo.worktree-dir=/tmp/worktrees",
    "opencode.config-path=/tmp/opencode.json",
    "opencode.planner.model=test",
    "opencode.test-writer.model=test",
    "opencode.test-reviewer.model=test",
    "opencode.coder-default.model=test",
    "mysql.url=jdbc:mysql://localhost:3306/test",
    "mysql.username=test",
    "mysql.password=test"
})
@AutoConfigureMockMvc
class TddForgeApplicationTests {

    @MockBean
    private ConfigValidator configValidator;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointShouldReturnUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
