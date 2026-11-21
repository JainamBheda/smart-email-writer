package com.smartemail.writer;

import com.smartemail.writer.app.EmailGeneratorController;
import com.smartemail.writer.app.EmailGeneratorService;
import com.smartemail.writer.app.EmailRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailGeneratorController.class)
class EmailGeneratorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmailGeneratorService emailGeneratorService;

    @Test
    void generatesReplyForValidRequest() throws Exception {
        when(emailGeneratorService.generateEmailReply(any(EmailRequest.class))).thenReturn("Thanks for your note.");

        mockMvc.perform(post("/api/email/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailRequest("Please confirm the date.", "friendly"))))
                .andExpect(status().isOk())
                .andExpect(content().string("Thanks for your note."));
    }

    @Test
    void rejectsBlankEmailContent() throws Exception {
        mockMvc.perform(post("/api/email/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailContent\":\"  \",\"tone\":\"casual\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("emailContent")));
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/api/email/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
