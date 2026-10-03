package com.cocacola.domain.repository;

import java.util.Map;

/** Puerto de CRM: envía contactos a un webhook (HubSpot, Salesforce, Zapier, Make, etc.). */
public interface CrmGateway {

    boolean isConfigured();

    void push(Map<String, Object> contact);
}
