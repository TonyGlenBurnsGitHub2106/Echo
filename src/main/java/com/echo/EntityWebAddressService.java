package com.echo;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EntityWebAddressService {

    private final EntityWebAddressRepository repo;

    public EntityWebAddressService(EntityWebAddressRepository repo) {
        this.repo = repo;
    }

    public List<Map<String, Object>> listForEntity(int entityId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : repo.listForEntity(entityId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entityWebAddressXRefId", row.get("EntityWebAddressXRefID"));
            item.put("webAddressId",           row.get("WebAddressID"));
            item.put("webAddressEntityId",     row.get("WebAddressEntityID"));
            item.put("webAddressName",         row.get("WebAddressName"));
            item.put("webAddress",             row.get("WebAddress"));
            item.put("webAddressTypeId",       row.get("WebAddressTypeID"));
            item.put("webAddressType",         row.get("WebAddressType"));
            item.put("webSiteTypeId",          row.get("WebSiteTypeID"));
            item.put("webSiteType",            row.get("WebSiteType"));
            item.put("notes",                  row.get("Notes"));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> save(int parentEntityId, int xrefId, WebAddressSaveRequest req) {
        String url = nz(req.webAddress);
        if (!url.isEmpty() && repo.existsByUrl(url, req.webAddressId)) {
            throw new DuplicateEntityException(
                "The web address '" + url + "' is already in use.");
        }
        Map<String, Object> row = repo.save(
            xrefId,
            req.webAddressId,
            parentEntityId,
            nz(req.webAddressName),
            url,
            req.webAddressTypeId,
            req.webSiteTypeId,
            nz(req.notes));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("webAddressId",           row.get("WebAddressID"));
        out.put("entityWebAddressXRefId", row.get("EntityWebAddressXRefID"));
        return out;
    }

    public void delete(int xrefId) { repo.delete(xrefId); }

    /** Web addresses not yet attached to this entity, projected to the
     *  camelCase shape the picker consumes. */
    public List<Map<String, Object>> listAvailable(int entityId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : repo.listAvailable(entityId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("webAddressId",   row.get("WebAddressID"));
            item.put("webAddressName", row.get("WebAddressName"));
            item.put("webAddress",     row.get("WebAddress"));
            item.put("webAddressType", row.get("WebAddressType"));
            result.add(item);
        }
        return result;
    }

    /** Attach an existing web address to an entity (xref only -- the shared
     *  web-address record is left untouched). */
    public void linkExisting(int parentEntityId, int webAddressId) {
        repo.linkExisting(parentEntityId, webAddressId);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public static class WebAddressSaveRequest {
        public int    webAddressId;
        public String webAddressName;
        public String webAddress;
        public int    webAddressTypeId;
        public int    webSiteTypeId;
        public String webSiteTypeName;       // Story 3525: a typed type, found or created when no id is given
        public String notes;
    }
}
