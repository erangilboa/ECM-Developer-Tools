export type Product = "DOCUMENTUM" | "EXTENDED_ECM";
export type Protocol =
  | "MOCK_DFC"
  | "DCTM_REST"
  | "LIVE_DFC"
  | "DFS"
  | "MOCK_OTCS"
  | "OTCS_REST"
  | "CWS";

export interface ConnectionProfile {
  id?: string;
  name: string;
  product: Product;
  protocol: Protocol;
  repository?: string;
  username?: string;
  restBaseUrl?: string;
  cgiRoot?: string;
  otdsUrl?: string;
  dfcLibDir?: string;
  dfcPropertiesPath?: string;
  reportedVersion?: string;
  authMode?: string;
}

export interface SessionView {
  id: string;
  profileId: string;
  profileName: string;
  product: Product;
  protocol: Protocol;
  repository: string;
  version: string;
  userName: string;
  idLabel: string;
  capabilities: string[];
}

export interface BrowseNode {
  id: string;
  name: string;
  typeName: string;
  subtype: number;
  folder: boolean;
  iconHint: string;
}

export interface FolderContents {
  parentId: string;
  parentName: string;
  children: BrowseNode[];
}

export interface AttributeValue {
  name: string;
  dataType: string;
  repeating: boolean;
  values: string[];
  readOnly: boolean;
}

export interface CategoryValue {
  categoryId: string;
  categoryName: string;
  attributes: Record<string, string[]>;
}

export interface ObjectDump {
  id: string;
  typeName: string;
  objectName: string;
  attributes: AttributeValue[];
  categories: CategoryValue[];
  extra: Record<string, string>;
  sapLinked: boolean;
}

export interface GridResult {
  columns: string[];
  rows: string[][];
  rowCount: number;
  query?: string;
  elapsedMs?: number;
}

export interface JobInfo {
  id: string;
  objectName: string;
  methodName: string;
  inactive: boolean;
  runNow: boolean;
  lastCompletionDate: string;
  nextInvocationDate: string;
  runInterval: string;
  lastReturn?: string;
  currentStatus?: string;
  status?: string;
}

export interface JobReport {
  id: string;
  objectName: string;
  created: string;
  contentType: string;
  subject: string;
}

export interface JobDetail {
  info: JobInfo;
  reports: JobReport[];
}

export interface BusinessWorkspace {
  id: string;
  name: string;
  templateId: string;
  extSystemId: string;
  boType: string;
  boId: string;
  parentId: string;
}

export interface TypeInfo {
  name: string;
  superName: string | null;
  attributes: string[];
}

export interface DumpTab {
  id: string;
  dump: ObjectDump;
}
