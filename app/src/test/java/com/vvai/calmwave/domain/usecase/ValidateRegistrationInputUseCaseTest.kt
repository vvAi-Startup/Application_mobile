package com.vvai.calmwave.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateRegistrationInputUseCaseTest {

    private val useCase = ValidateRegistrationInputUseCase()

    @Test
    fun `should return invalid when fields are blank`() {
        val result = useCase.execute("", "", "", "")

        assertTrue(result is RegistrationValidationResult.Invalid)
        assertEquals("Preencha todos os campos", (result as RegistrationValidationResult.Invalid).message)
    }

    @Test
    fun `should return invalid when email is malformed`() {
        val result = useCase.execute("Nome", "email-invalido", "Senha@123", "Senha@123")

        assertTrue(result is RegistrationValidationResult.Invalid)
        assertEquals("Email inválido", (result as RegistrationValidationResult.Invalid).message)
    }

    @Test
    fun `should return invalid when password is weak`() {
        val result = useCase.execute("Nome", "teste@email.com", "12345678", "12345678")

        assertTrue(result is RegistrationValidationResult.Invalid)
        assertEquals(
            "A senha deve ter no mínimo 8 caracteres, com letra, número e caractere especial",
            (result as RegistrationValidationResult.Invalid).message
        )
    }

    @Test
    fun `should return invalid when passwords do not match`() {
        val result = useCase.execute("Nome", "teste@email.com", "Senha@123", "Senha@124")

        assertTrue(result is RegistrationValidationResult.Invalid)
        assertEquals("As senhas não coincidem", (result as RegistrationValidationResult.Invalid).message)
    }

    @Test
    fun `should return valid for valid inputs`() {
        val result = useCase.execute("Nome", "teste@email.com", "Senha@123", "Senha@123")

        assertTrue(result is RegistrationValidationResult.Valid)
    }
}
