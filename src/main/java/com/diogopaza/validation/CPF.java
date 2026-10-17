package com.diogopaza.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//Em que nivel vai ter a retencao da anotacao - sao as profundidades -
@Retention(RetentionPolicy.RUNTIME)
//Onde essa anotacao sera utilizada - campos, atributos, classes e etc. No nosso caso FIELD e direto no atributo.
@Target(ElementType.FIELD)
@Constraint(validatedBy = CpfConstraintValidator.class)
public @interface CPF {

    String message() default "CPF inválido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

}


