package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.model.ContactSource;
import com.myhive.backend.repository.ContactRepository;
import com.myhive.backend.service.ContactService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.myhive.backend.util.JwtTestHelper.adminJwt;
import static com.myhive.backend.util.JwtTestHelper.managerJwt;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Not @Transactional: contacts are written through ContactService.touch (REQUIRES_NEW).
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class AdminContactsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ContactService contactService;
    @Autowired private ContactRepository contactRepository;

    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) {
            contactRepository.findByEmail(email).ifPresent(contactRepository::delete);
        }
    }

    private String seedContact(String marker) {
        String email = "c-" + marker + "-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        contactService.touch(email, ContactSource.VOTE, null, null);
        return email;
    }

    @Test
    void listContacts_admin_filtersByQuery() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String expectedEmail = seedContact(marker);
        seedContact("other");

        mockMvc.perform(get("/admin/contacts").param("q", marker).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].email", is(expectedEmail)))
                .andExpect(jsonPath("$.content[0].firstSource", is("VOTE")))
                .andExpect(jsonPath("$.content[0].unsubscribed", is(false)));
    }

    @Test
    void listContacts_manager_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/contacts").with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listContacts_outOfRangePaging_isClampedNot500() throws Exception {
        mockMvc.perform(get("/admin/contacts").param("page", "-1").param("size", "0").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.size", is(1)));
    }

    @Test
    void exportContacts_admin_returnsCsvAttachment() throws Exception {
        String expectedEmail = seedContact("csv");

        mockMvc.perform(get("/admin/contacts/export").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("contacts-")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("email,name,locale,first_source")))
                .andExpect(content().string(containsString(expectedEmail)));
    }

    @Test
    void exportContacts_manager_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/contacts/export").with(managerJwt()))
                .andExpect(status().isForbidden());
    }
}
