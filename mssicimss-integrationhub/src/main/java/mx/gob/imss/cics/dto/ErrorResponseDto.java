package mx.gob.imss.cics.dto;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import lombok.Data;

@Data
public class ErrorResponseDto {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private List<String> messages;
    private String path;

    public ErrorResponseDto(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.messages = Collections.singletonList(message);
        this.path = path;
    }

    public ErrorResponseDto(int status, String error, List<String> messages, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.messages = messages;
        this.path = path;
    }


 
}