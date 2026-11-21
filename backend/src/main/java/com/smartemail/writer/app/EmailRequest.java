package com.smartemail.writer.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {

    @NotBlank(message = "emailContent is required")
    @Size(max = 20000, message = "emailContent must be 20000 characters or fewer")
    private String emailContent;

    @Size(max = 60, message = "tone must be 60 characters or fewer")
    private String tone;
}
