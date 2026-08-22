package com.berkay.crm.dto;

import com.berkay.crm.model.Account;

public record AccountResponse(Long id, Integer version, String name, String industry,
                              String website, String phone, Long ownerId,
                              String ownerName, long contactCount
                              ) {

    public static AccountResponse from(Account account, long contactCount) {

        return new AccountResponse(
                account.getId(), account.getVersion(), account.getName(),
                account.getIndustry(), account.getWebsite(), account.getPhone(),
                account.getOwner().getId(), account.getOwner().getUsername(),
                contactCount
        );
    }
}
