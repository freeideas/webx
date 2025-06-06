# Augment Agent Coding Instructions

## CRITICAL: Pre-Code Checklist

Before writing ANY code, you MUST:

1. **Read `CODE_GUIDELINES.md`** - This contains mandatory formatting and style rules
2. **Follow brevity principle** - Fewer lines of code beats almost every other concern
3. **Use early returns** - Avoid nesting, keep code flat
4. **Apply spacing rules** - Spaces after `(` and before `)` when containing punctuation
5. **Limit lines to 120 characters**
6. **Remove unnecessary comments** - Only keep comments with "NOTE" or immediately after `{`

## Key Guidelines Summary

- Single-line methods when possible: `public void method() { callMethod(); }`
- Early returns: `if (condition) return;` instead of nested if blocks
- Flat loops: `if (!condition) break;` instead of nested conditions
- No spaces around comparison: `a==b`, `i<10`
- Spaces around logical: `a==b && b==c`
- Method calls with punctuation: `methodName( arg1, arg2 )`
- Method calls without punctuation: `methodName()`

## Testing Requirements

After modifying Java files with `_TEST_` methods:
```bash
./java/javac.sh && ./java/java.sh [ClassName]
```

## Reference Files

- `CODE_GUIDELINES.md` - Complete formatting rules (READ THIS FIRST)
- `CLAUDE.md` - Build commands and project structure
