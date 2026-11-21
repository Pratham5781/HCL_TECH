package com.utility.billing.common;
import java.time.Instant;
import java.util.List;
public record ApiResponse<T>(boolean success, String message, T data, List<String> errors, Instant timestamp) {
 public static <T> ApiResponse<T> ok(String message,T data){return new ApiResponse<>(true,message,data,List.of(),Instant.now());}
 public static <T> ApiResponse<T> failure(String message,List<String> errors){return new ApiResponse<>(false,message,null,errors,Instant.now());}
}

