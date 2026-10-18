package com.diogopaza.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DigitosVerificadorCpfTest {

    @Test
    void testaDigitoVerificador() {
        DigitosVerificadoresCpf esperado = new DigitosVerificadoresCpf(3,5);
        assertEquals(esperado, CpfConstraintValidator.calculaDigitoVerificadores("111444777"));
    }
}
