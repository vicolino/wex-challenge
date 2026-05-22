package com.wex.challenge.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.wex.challenge.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void beanValidationFallsBackToInvalidWhenDefaultMessageIsNull() throws Exception {
        BindingResult br = new BeanPropertyBindingResult(new Object(), "target");
        br.addError(new FieldError("target", "fieldX", null, false, null, null, null));

        Method anyMethod = GlobalExceptionHandlerTest.class.getDeclaredMethod("anchor", String.class);
        MethodParameter parameter = new MethodParameter(anyMethod, 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, br);

        ProblemDetail pd = handler.handleBeanValidation(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) pd.getProperties().get("errors");
        assertThat(errors).singleElement()
                .satisfies(err -> {
                    assertThat(err.get("field")).isEqualTo("fieldX");
                    assertThat(err.get("message")).isEqualTo("invalid");
                });
    }

    @SuppressWarnings("unused")
    private void anchor(String s) {
    }
}
