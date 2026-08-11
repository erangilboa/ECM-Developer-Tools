export const DQL_KEYWORDS = [
  "SELECT",
  "FROM",
  "WHERE",
  "AND",
  "OR",
  "NOT",
  "LIKE",
  "IN",
  "ORDER",
  "BY",
  "ENABLE",
  "RETURN_TOP",
  "FTDQL",
  "FOLDER",
  "CABINET",
  "UPDATE",
  "OBJECTS",
  "SET",
  "DELETE",
  "CREATE",
  "GROUP",
  "HAVING",
  "UNION",
  "SYNONYM",
];

export const DQL_FUNCTIONS = [
  "COUNT",
  "SUM",
  "AVG",
  "MIN",
  "MAX",
  "DATE",
  "DATETOSTRING",
  "UPPER",
  "LOWER",
  "SUBSTR",
];

export function registerDql(monaco: typeof import("monaco-editor")) {
  monaco.languages.register({ id: "dql" });
  monaco.languages.setMonarchTokensProvider("dql", {
    ignoreCase: true,
    keywords: DQL_KEYWORDS,
    tokenizer: {
      root: [
        [/[a-zA-Z_][\w$]*/, { cases: { "@keywords": "keyword", "@default": "identifier" } }],
        [/'[^']*'/, "string"],
        [/"[^"]*"/, "string"],
        [/[0-9]+/, "number"],
        [/--.*$/, "comment"],
      ],
    },
  });
}
