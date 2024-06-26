package com.bizorder.service.impl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.bizorder.dtos.LoginResponse;
import com.bizorder.dtos.LoginUserDto;
import com.bizorder.dtos.RegisterUserDto;
import com.bizorder.exception.SearchNotFoundException;
import com.bizorder.model.ResetToken;
import com.bizorder.model.Seller;
import com.bizorder.model.VerificationToken;
import com.bizorder.model.Account;
import com.bizorder.repository.ResetTokenRepository;
import com.bizorder.repository.SellerRepository;
import com.bizorder.repository.VerificationTokenRepository;
import com.bizorder.repository.AccountRepository;
import com.bizorder.service.AuthenticationService;
import com.bizorder.service.JwtService;

@Service
public class AuthenticationServiceImpl implements AuthenticationService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final ResetTokenRepository resetTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final SellerRepository sellerRepository;

    public AuthenticationServiceImpl(AccountRepository accountRepository, AuthenticationManager authenticationManager, PasswordEncoder passwordEncoder, JwtService jwtService, ResetTokenRepository resetTokenRepository, VerificationTokenRepository verificationTokenRepository, SellerRepository sellerRepository) {
        this.accountRepository = accountRepository;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.resetTokenRepository = resetTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.sellerRepository = sellerRepository;
    }

    @Override
    public Account signup(RegisterUserDto input) {

        Optional<Account> existingAccount = accountRepository.findByEmail(input.getEmail());
        if (existingAccount.isPresent()) {
            throw new SearchNotFoundException("Email already exists");
        }
    
        Seller seller = sellerRepository.findById(input.getSellerId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid seller ID"));
                
        Account user = new Account()
            .setFullName(input.getFullName())
            .setEmail(input.getEmail())
            .setPassword(passwordEncoder.encode(input.getPassword()))
            .setStatus("Unverified")
            .setSeller(seller);

        return accountRepository.save(user);
    }

    @Override
    public Account authenticate(LoginUserDto input) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                input.getEmail(),
                input.getPassword()
            )
        );

        return accountRepository.findByEmail(input.getEmail())
            .orElseThrow();
    }

    @Override
    public LoginResponse authenticateAndGenerateResponse(LoginUserDto loginUserDto) {
        Account authenticatedUser = authenticate(loginUserDto);
        if (authenticatedUser.getStatus().equals("Unverified")) {
            throw new SearchNotFoundException("Account is not verified");
        }
        String jwtToken = jwtService.generateToken(authenticatedUser);
        return new LoginResponse().setToken(jwtToken).setExpiresIn(jwtService.getExpirationTime());
    }

    @Override
    public Integer generateAndSaveVerificationToken(String email) {
        Integer token = ThreadLocalRandom.current().nextInt(100000, 1000000);

        VerificationToken tokenEntity = new VerificationToken(token, email);
        verificationTokenRepository.save(tokenEntity);

        return token;
    }

    @Override
    public void verifyAccount(String email, Integer token) {
        Optional<Account> optionalUser = accountRepository.findByEmail(email);

        Account user = optionalUser.orElseThrow(() -> new SearchNotFoundException("Account does not exist, please register your account"));

        Optional<VerificationToken> optionalVerificationToken = verificationTokenRepository.findByEmail(email);
        if (!optionalVerificationToken.isPresent()) {
            throw new SearchNotFoundException("Token missing");
        }

        Integer verificationToken = verificationTokenRepository.getVerificationTokenByEmail(email);
        
        System.out.println(verificationToken);
        System.out.println(token);
        if (!(verificationToken != null && verificationToken.equals(token))) {
            throw new SearchNotFoundException("Verification token is incorrect");
        }

        Date expiry = verificationTokenRepository.getExpiresAtByEmail(email);
        Date currentDate = new Date(); 

        if (!currentDate.before(expiry)) {
            verificationTokenRepository.deleteByVerificationToken(verificationToken);
            throw new SearchNotFoundException("Token has expired. Please generate a new one.");
        }

        verificationTokenRepository.deleteByVerificationToken(verificationToken);
        user.setStatus("Verified");
        accountRepository.save(user);
    }

    @Override
    public boolean updatePassword(String email, String newPassword) {
        Optional<Account> optionalUser = accountRepository.findByEmail(email);
        if (optionalUser.isPresent()) {
            Account user = optionalUser.get();
            user.setPassword(passwordEncoder.encode(newPassword));
            accountRepository.save(user);
            return true;
        }
        return false;
    }

    @Override
    public String hashResetToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(token.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String generateAndSaveResetToken(String email) {
        String resetToken = jwtService.generateResetToken(email);

        String hashedToken = hashResetToken(resetToken);

        ResetToken tokenEntity = new ResetToken(hashedToken, email);
        resetTokenRepository.save(tokenEntity);
        
        System.out.println("resetToken");
        System.out.println(resetToken);
        System.out.println("hashedToken");
        System.out.println(hashedToken);

        return resetToken;
    }

    @Override
    public boolean resetPassword(String email, String token, String newPassword) {
        Optional<Account> optionalUser = accountRepository.findByEmail(email);
        Account user = optionalUser.orElseThrow(() -> new RuntimeException("User not found"));

        Optional<ResetToken> optionalResetToken = resetTokenRepository.findByEmail(email);
        if (!optionalResetToken.isPresent()) {
            // token not found
            return false;
        }

        String storedHashedToken = resetTokenRepository.getResetTokenByEmail(email);
        String hashedToken = hashResetToken(token);

        System.out.println("storedHashedToken");
        System.out.println(storedHashedToken);
        System.out.println("hashedToken");
        System.out.println(hashedToken);



        if (!(storedHashedToken != null && storedHashedToken.equals(hashedToken))) {
            throw new SearchNotFoundException("Reset token is incorrect");
        }

        Date expiry = resetTokenRepository.getExpiresAtByEmail(email);
        Date currentDate = new Date(); 

        if (!currentDate.before(expiry)) {
            // token expired
            resetTokenRepository.deleteByResetToken(storedHashedToken);
            throw new SearchNotFoundException("Token has expired. Please generate a new one.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));

        // Save the updated user
        accountRepository.save(user);

        resetTokenRepository.deleteByResetToken(storedHashedToken);

        return true;
    }
}