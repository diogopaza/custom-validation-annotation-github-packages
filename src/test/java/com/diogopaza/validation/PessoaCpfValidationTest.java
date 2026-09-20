package com.diogopaza.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Contract these tests assume:
 *  - annotation:  com.diogopaza.validation.CPF
 *  - validator:   com.diogopaza.validation.CpfConstraintValidator
 *  - accepted formats: exactly 11 digits, or the mask ###.###.###-## (no trimming)
 *  - null is valid (Bean Validation convention; combine with @NotNull to forbid it)
 *  - "" and blank strings are invalid
 *  - default message: "CPF inválido"
 */
class PessoaCpfValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static Set<ConstraintViolation<Pessoa>> validate(String cpf) {
        return validator.validate(new Pessoa("Diogo", cpf));
    }

    static class DocumentoCustomizado {
        @CPF(message = "documento inválido")
        String cpf = "111.111.111-11";
    }

    @Nested
    @DisplayName("Etapa 2 - contrato da anotação @CPF (reflection, não depende da lógica do validador)")
    class EtapaAnotacao {

        @Test
        @DisplayName("[obrigatório] @Retention(RUNTIME)")
        void retentionRuntime() {
            Retention retention = CPF.class.getAnnotation(Retention.class);
            assertNotNull(retention, "faltou @Retention na @CPF");
            assertEquals(RetentionPolicy.RUNTIME, retention.value());
        }

        @Test
        @DisplayName("[obrigatório] @Target inclui FIELD")
        void targetIncluiField() {
            Target target = CPF.class.getAnnotation(Target.class);
            assertNotNull(target, "faltou @Target na @CPF");
            assertTrue(Arrays.asList(target.value()).contains(ElementType.FIELD));
        }

        @Test
        @DisplayName("[obrigatório] @Constraint aponta para CpfConstraintValidator")
        void marcadaComoConstraint() {
            Constraint constraint = CPF.class.getAnnotation(Constraint.class);
            assertNotNull(constraint, "faltou @Constraint na @CPF");
            assertTrue(Arrays.asList(constraint.validatedBy()).contains(CpfConstraintValidator.class));
        }

        @Test
        @DisplayName("[obrigatório] message() tem default \"CPF inválido\"")
        void mensagemPadrao() throws NoSuchMethodException {
            assertEquals("CPF inválido", CPF.class.getMethod("message").getDefaultValue());
        }

        @Test
        @DisplayName("[obrigatório] groups() e payload() existem com default vazio")
        void groupsEPayloadComDefaultVazio() throws NoSuchMethodException {
            assertArrayEquals(new Class<?>[0], (Class<?>[]) CPF.class.getMethod("groups").getDefaultValue());
            assertArrayEquals(new Class<?>[0], (Class<?>[]) CPF.class.getMethod("payload").getDefaultValue());
        }
    }

    @Nested
    @DisplayName("Etapa 3 - regras do CpfConstraintValidator (validando a Pessoa de verdade)")
    class EtapaValidador {

        @ParameterizedTest(name = "[obrigatório] aceita CPF válido: {0}")
        @ValueSource(strings = {
                "529.982.247-25", "52998224725",
                "111.444.777-35", "11144477735",
                "123.456.789-09", "12345678909"
        })
        void aceitaCpfValido(String cpf) {
            assertTrue(validate(cpf).isEmpty());
        }

        @ParameterizedTest(name = "[obrigatório] rejeita dígito verificador errado: {0}")
        @ValueSource(strings = {
                "529.982.247-26", "52998224720", "529.982.247-35", "123.456.789-00"
        })
        void rejeitaDigitoVerificadorErrado(String cpf) {
            assertFalse(validate(cpf).isEmpty());
        }

        @ParameterizedTest(name = "[obrigatório] rejeita todos os dígitos iguais: {0}")
        @ValueSource(strings = {
                "000.000.000-00", "00000000000",
                "111.111.111-11", "222.222.222-22", "99999999999"
        })
        void rejeitaTodosOsDigitosIguais(String cpf) {
            assertFalse(validate(cpf).isEmpty());
        }

        @ParameterizedTest(name = "[obrigatório] rejeita tamanho/formato inválido: [{0}]")
        @ValueSource(strings = {
                "1234567890", "123456789012",
                "529.982.247-2", "529982247-25", "529.982.24725", "529.982.247.25",
                " 529.982.247-25 ", "", "   "
        })
        void rejeitaTamanhoOuFormatoInvalido(String cpf) {
            assertFalse(validate(cpf).isEmpty());
        }

        @ParameterizedTest(name = "[obrigatório] rejeita não numérico sem lançar exceção: {0}")
        @ValueSource(strings = {"abc.def.ghi-jk", "529.982.247-2a", "5299822472a"})
        void rejeitaNaoNumerico(String cpf) {
            assertFalse(validate(cpf).isEmpty());
        }

        @ParameterizedTest(name = "[obrigatório] null é válido (convenção Bean Validation)")
        @NullSource
        void nullEhValido(String cpf) {
            assertTrue(validate(cpf).isEmpty());
        }

        @Test
        @DisplayName("[obrigatório] violação aponta para o campo cpf com a mensagem padrão")
        void violacaoTemCampoEMensagemPadrao() {
            Set<ConstraintViolation<Pessoa>> violations = validate("111.111.111-11");

            assertEquals(1, violations.size());
            ConstraintViolation<Pessoa> violation = violations.iterator().next();
            assertEquals("cpf", violation.getPropertyPath().toString());
            assertEquals("CPF inválido", violation.getMessage());
        }

        @Test
        @DisplayName("[obrigatório] message customizada na anotação é respeitada")
        void mensagemCustomizadaEhRespeitada() {
            Set<ConstraintViolation<DocumentoCustomizado>> violations =
                    validator.validate(new DocumentoCustomizado());

            assertEquals(1, violations.size());
            assertEquals("documento inválido", violations.iterator().next().getMessage());
        }
    }
}
