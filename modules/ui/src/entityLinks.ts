import type { Product } from "./types";

export type EntityLinkKind = "object" | "type" | "acl" | "folder" | "job" | "none";

export type EntityLink = {
  kind: EntityLinkKind;
  value: string;
  label?: string;
};

const DOC_OBJECT_ID = /^[0-9a-fA-F]{16}$/;
const OTCS_NODE_ID = /^\d{4,}$/;

/** Attribute names that typically hold navigable object/folder ids. */
const FOLDER_ATTRS = new Set([
  "i_folder_id",
  "r_folder_id",
  "parent_id",
  "r_link_cnt",
  "r_version_label",
  "r_chronicle_id",
  "r_object_id",
  "id",
]);

const TYPE_ATTRS = new Set(["r_object_type", "type_name", "type"]);
const ACL_ATTRS = new Set(["acl_name", "acl_domain", "default_acl"]);

export function looksLikeObjectId(value: string, product?: Product): boolean {
  const v = (value || "").trim();
  if (!v) return false;
  if (product === "EXTENDED_ECM") return OTCS_NODE_ID.test(v);
  return DOC_OBJECT_ID.test(v);
}

export function entityLinkForAttribute(
  attrName: string,
  value: string,
  product?: Product
): EntityLink {
  const v = (value || "").trim();
  if (!v) return { kind: "none", value: v };

  const name = (attrName || "").toLowerCase();

  if (FOLDER_ATTRS.has(name) || name.endsWith("_id") || name.includes("object_id")) {
    if (looksLikeObjectId(v, product)) return { kind: "object", value: v };
  }

  if (TYPE_ATTRS.has(name)) {
    return { kind: "type", value: v, label: v };
  }

  if (ACL_ATTRS.has(name)) {
    return { kind: "acl", value: v, label: v };
  }

  if (looksLikeObjectId(v, product)) {
    return { kind: "object", value: v };
  }

  return { kind: "none", value: v };
}

export function iapiTemplates(objectId: string): { label: string; command: string }[] {
  return [
    { label: "dump,c", command: `dump,c,${objectId}` },
    { label: "fetch,c", command: `fetch,c,${objectId}` },
    { label: "checkout,c", command: `checkout,c,${objectId}` },
    { label: "get,c", command: `get,c,${objectId}` },
  ];
}

export function aclPeekDql(aclName: string, domain = "docbase"): string {
  return `SELECT r_object_id, object_name, owner_name, world_permit FROM dm_acl WHERE object_name = '${aclName.replace(/'/g, "''")}' AND acl_domain = '${domain.replace(/'/g, "''")}'`;
}
