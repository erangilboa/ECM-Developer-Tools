export const IAPI_METHODS = [
  "abort",
  "append",
  "apply",
  "archive",
  "assemble",
  "bindfile",
  "cachequery",
  "cancelcheckout",
  "checkin",
  "checkout",
  "close",
  "commit",
  "connect",
  "count",
  "create",
  "decryptfile",
  "demote",
  "destroy",
  "disconnect",
  "dump",
  "encryptfile",
  "execquery",
  "execreadquery",
  "fetch",
  "get",
  "getbase",
  "getconnection",
  "getcontent",
  "getdocbasemap",
  "getevent",
  "getfile",
  "getlastcoll",
  "getmessage",
  "getpath",
  "getservermap",
  "id",
  "insert",
  "link",
  "listappend",
  "listinsert",
  "listremove",
  "locate",
  "lock",
  "next",
  "print",
  "promote",
  "query",
  "readquery",
  "remove",
  "repeat",
  "retrieve",
  "save",
  "saveasnew",
  "set",
  "setbatchhint",
  "setcontent",
  "setfile",
  "setoutput",
  "setpath",
  "trace",
  "truncate",
  "unlink",
  "unlock",
  "useacl",
];

export function registerIapi(monaco: typeof import("monaco-editor")) {
  monaco.languages.register({ id: "iapi" });
  monaco.languages.setLanguageConfiguration("iapi", {
    comments: { lineComment: "#" },
    autoClosingPairs: [
      { open: "'", close: "'" },
      { open: "(", close: ")" },
    ],
  });
  monaco.languages.setMonarchTokensProvider("iapi", {
    ignoreCase: true,
    methods: IAPI_METHODS,
    tokenizer: {
      root: [
        [/[a-zA-Z_][\w]*/, { cases: { "@methods": "keyword", c: "variable", l: "variable", "@default": "identifier" } }],
        [/'([^']|'')*'/, "string"],
        [/'([^']|'')*$/, "string.invalid"],
        [/[0-9a-fA-F]{16}/, "number.hex"],
        [/,/, "delimiter"],
      ],
    },
  });
  monaco.languages.registerCompletionItemProvider("iapi", {
    triggerCharacters: [","],
    provideCompletionItems: (model, position) => {
      const text = model.getValue().slice(0, model.getOffsetAt(position));
      const commaCount = (text.match(/,/g) || []).length;
      const word = model.getWordUntilPosition(position);
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };
      if (commaCount === 0) {
        return {
          suggestions: IAPI_METHODS.map((m) => ({
            label: m,
            kind: monaco.languages.CompletionItemKind.Keyword,
            insertText: m,
            range,
          })),
        };
      }
      if (commaCount === 1) {
        return {
          suggestions: ["c", "l"].map((s) => ({
            label: s,
            kind: monaco.languages.CompletionItemKind.Variable,
            insertText: s,
            range,
          })),
        };
      }
      return { suggestions: [] };
    },
  });
}
