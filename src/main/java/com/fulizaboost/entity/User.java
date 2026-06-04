package com.fulizaboost.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    private String country;

    @Column(nullable = false)
    private String password;

    private Double balance = 0.0;
    private Double totalEarned = 0.0;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String u) { this.username = u; }
    public String getEmail() { return email; }
    public void setEmail(String e) { this.email = e; }
    public String getPhone() { return phone; }
    public void setPhone(String p) { this.phone = p; }
    public String getCountry() { return country; }
    public void setCountry(String c) { this.country = c; }
    public String getPassword() { return password; }
    public void setPassword(String p) { this.password = p; }
    public Double getBalance() { return balance; }
    public void setBalance(Double b) { this.balance = b; }
    public Double getTotalEarned() { return totalEarned; }
    public void setTotalEarned(Double t) { this.totalEarned = t; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
