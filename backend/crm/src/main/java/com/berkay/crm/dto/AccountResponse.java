package com.berkay.crm.dto;

import com.berkay.crm.model.Account;

public record AccountResponse(Long id, String name, String industry,
                              String website, String phone, Long ownerId, String ownerName) {

    public static AccountResponse from(Account account) {

        return new AccountResponse(
                account.getId(), account.getName(), account.getIndustry(),
                account.getWebsite(), account.getPhone(),
                account.getOwner().getId(), account.getOwner().getUsername()
        );
    }
}
