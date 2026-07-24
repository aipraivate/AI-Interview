package com.interview.platform.auth;

import com.interview.platform.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/guest")
    ApiResponse<AuthService.GuestLogin> guest(@Valid @RequestBody(required = false) GuestRequest request) {
        return ApiResponse.ok(authService.loginAsGuest(request == null ? null : request.nickname()));
    }

    @PostMapping("/register")
    ApiResponse<AuthService.GuestLogin> register(@AuthenticationPrincipal String currentUserId,
                                                 @Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(new AuthService.RegisterCommand(
                request.email(), request.password(), request.nickname(),
                request.acceptTerms(), request.acceptPrivacy()), currentUserId));
    }

    @PostMapping("/login")
    ApiResponse<AuthService.GuestLogin> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(new AuthService.LoginCommand(request.email(), request.password())));
    }

    @PostMapping("/refresh")
    ApiResponse<AuthService.GuestLogin> refresh(@Valid @RequestBody TokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@Valid @RequestBody TokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    ApiResponse<AuthService.UserView> me(@AuthenticationPrincipal String userId) {
        return ApiResponse.ok(authService.me(userId));
    }

    record GuestRequest(@Size(max = 80) String nickname) {}
    record RegisterRequest(@Email @NotBlank @Size(max = 190) String email,
                           @NotBlank @Size(min = 10, max = 72)
                           @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") String password,
                           @NotBlank @Size(max = 80) String nickname,
                           boolean acceptTerms, boolean acceptPrivacy) {}
    record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    record TokenRequest(@NotBlank String refreshToken) {}
}
