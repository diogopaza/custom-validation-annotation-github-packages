package com.diogopaza.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class CpfConstraintValidator implements ConstraintValidator<CPF, String> {

    // 11 digitos puros ou a mascara ###.###.###-## completa, sem espacos
    private static final Pattern FORMATO_CPF = Pattern.compile("\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
    private static final Pattern DIGITOS_IGUAIS = Pattern.compile("(\\d)\\1{10}");

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        if (s == null) {
            return true;
        }
        if (!formatoValido(s)) {
            return false;
        }
        String novoCpf = normalizeCpf(s);
        DigitosVerificadoresCpf digitosVerificadoresCpf = calculaDigitoVerificadores(novoCpf);
        if (!comparaDigitoVerificador(novoCpf, digitosVerificadoresCpf) || todosDigitosIguais(novoCpf)) {
            return false;
        }
        return true;
    }

    static boolean formatoValido(String cpf) {
        return FORMATO_CPF.matcher(cpf).matches();
    }

    static boolean todosDigitosIguais(String cpfNormalizado) {
        return DIGITOS_IGUAIS.matcher(cpfNormalizado).matches();
    }

    // Chamar so depois de formatoValido: aqui so os separadores da mascara sao removidos
    static String normalizeCpf(String cpf) {
        return cpf.replace(".", "").replace("-", "");
    }

    static DigitosVerificadoresCpf calculaDigitoVerificadores(String cpf) {

        int primeiroDigitoVerificador = calculaUmDigitoVerificador(cpf, null);
        int segundoDigitoVerificador = calculaUmDigitoVerificador(cpf, String.valueOf(primeiroDigitoVerificador));

        return new DigitosVerificadoresCpf(primeiroDigitoVerificador, segundoDigitoVerificador);
    }

    static int calculaUmDigitoVerificador(String cpf, String primeiroDigito) {
        int somatorio = 0;
        int variavelControle = primeiroDigito == null ? 11 : 12;
        for (int i = 1; i < 10; i++) {
            String digito = String.valueOf(cpf.charAt(i - 1));
            int resultado = Integer.parseInt(digito) * (variavelControle - i);
            somatorio += resultado;
        }
        if(primeiroDigito != null) {
            somatorio += Integer.parseInt(primeiroDigito) * 2;
        }
        int modSomatorio = somatorio % 11;
        return modSomatorio < 2 ? 0 : 11 - modSomatorio;
    }

    static boolean comparaDigitoVerificador(String cpf, DigitosVerificadoresCpf digitosCalculados) {
        int digito1 = Character.getNumericValue(cpf.charAt(9));
        int digito2 = Character.getNumericValue(cpf.charAt(10));

        return digito1 == digitosCalculados.primeiroDigitoVerificador()
                && digito2 == digitosCalculados.segundoDigitoVerificador();
    }

}

