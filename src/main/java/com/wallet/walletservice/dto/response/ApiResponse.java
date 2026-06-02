package com.wallet.walletservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data @Builder
@Schema(description = "Standard API response wrapper")
public class ApiResponse<T> {
    @Schema(description = "Indicates if the operation was successful", example = "true")
    private boolean success;

    @Schema(description = "Descriptive message about the result", example = "Operation successful")
    private String message;

    @Schema(description = "The payload of the response")
    private T data ;

    public static <T> ApiResponse<T> ok(T data){
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> ok(String message, T data){
        return ApiResponse.<T>builder().success(true).message(message).data(data).build();
    }

    public static <T> ApiResponse<T> error(String message){
        return ApiResponse.<T>builder().success(false).message(message).build();
    }

    public static <T> ApiResponse<T> error(String message, T data){
        return ApiResponse.<T>builder().success(false).message(message).data(data).build();
    }
}
