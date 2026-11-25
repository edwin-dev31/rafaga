package com.rafaga.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantAdminSearchIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void paginatesAndFiltersTenantsByNameDomainStatusPlanAndDeploymentMode() throws Exception {
        String tag = "srch" + UUID.randomUUID().toString().substring(0, 8);
        var superAdmin = jwt().jwt(token -> token.subject("kc-super-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));

        String proId = registerTenant(superAdmin, "Alpha " + tag, "alpha-" + tag + ".rafaga.test", "pro");
        String entId = registerTenant(superAdmin, "Beta " + tag, "beta-" + tag + ".rafaga.test", "enterprise");
        String inactiveId = registerTenant(superAdmin, "Gamma " + tag, "gamma-" + tag + ".rafaga.test", "starter");

        mvc.perform(post("/api/admin/tenants/{tenantId}/deactivate", inactiveId).with(superAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        // 1. Absent q works without error and paginates
        mvc.perform(get("/api/admin/tenants").param("page", "0").param("size", "2").with(superAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2));

        // 2. Search by unique tag matches all 3 tenants and paginates
        mvc.perform(get("/api/admin/tenants")
                        .param("q", tag)
                        .param("page", "0")
                        .param("size", "2")
                        .with(superAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2));

        // 3. Search by domain substring via TenantDomainControl subquery
        mvc.perform(get("/api/admin/tenants")
                        .param("q", "alpha-" + tag + ".rafaga.test")
                        .with(superAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(proId));

        // 4. Filter by status=INACTIVE + q
        mvc.perform(get("/api/admin/tenants")
                        .param("q", tag)
                        .param("status", "INACTIVE")
                        .with(superAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inactiveId));

        // 5. Filter by planId=enterprise + deploymentMode=SHARED_DB + q
        mvc.perform(get("/api/admin/tenants")
                        .param("q", tag)
                        .param("planId", "enterprise")
                        .param("deploymentMode", "SHARED_DB")
                        .with(superAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(entId));
    }

    private String registerTenant(
            org.springframework.test.web.servlet.request.RequestPostProcessor superAdmin,
            String name,
            String domain,
            String planId) throws Exception {
        MvcResult result = mvc.perform(post("/api/admin/tenants")
                        .with(superAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "domains": ["%s"],
                                  "deploymentMode": "SHARED_DB",
                                  "planId": "%s"
                                }
                                """.formatted(name, domain, planId)))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }
}
