package com.vvai.calmwave.domain.usecase

sealed class RegistrationValidationResult {
    data object Valid : RegistrationValidationResult()
    data class Invalid(val message: String) : RegistrationValidationResult()
}

class ValidateRegistrationInputUseCase {
    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val passwordRegex = Regex("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$")

    fun execute(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): RegistrationValidationResult {
        if (name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            return RegistrationValidationResult.Invalid("Preencha todos os campos")
        }
        if (!emailRegex.matches(email.trim())) {
            return RegistrationValidationResult.Invalid("Email inválido")
        }
        if (!passwordRegex.matches(password)) {
            return RegistrationValidationResult.Invalid("A senha deve ter no mínimo 8 caracteres, com letra, número e caractere especial")
        }
        if (password != confirmPassword) {
            return RegistrationValidationResult.Invalid("As senhas não coincidem")
        }
        return RegistrationValidationResult.Valid
    }
}
