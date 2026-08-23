package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.service.DestinationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, DestinationCategoryControllerTest.MockConfig.class})
class DestinationCategoryControllerTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public DestinationService destinationService() {
            return mock(DestinationService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        reset(destinationService);
    }

    @Test
    void getCategoriesForDestination_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        CategoryDTO category = new CategoryDTO(UUID.randomUUID(), "Adventure", "adventure");
        when(destinationService.getCategoriesForDestination(eq(id), isNull())).thenReturn(List.of(category));

        mockMvc.perform(get("/destinations/{id}/categories", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Adventure"))
                .andExpect(jsonPath("$[0].slug").value("adventure"));
    }

    @Test
    void getCategoriesForDestination_emptyResult_returnsEmptyArray() throws Exception {
        UUID id = UUID.randomUUID();
        when(destinationService.getCategoriesForDestination(eq(id), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/destinations/{id}/categories", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void updateDestinationCategories_noAuth_returnsUnauthorized() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/admin/destinations/{id}/categories", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateDestinationCategories_asManager_returnsForbidden() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/admin/destinations/{id}/categories", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateDestinationCategories_asAdmin_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(destinationService).updateDestinationCategories(eq(id), any());

        mockMvc.perform(put("/admin/destinations/{id}/categories", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isNoContent());
    }
}
