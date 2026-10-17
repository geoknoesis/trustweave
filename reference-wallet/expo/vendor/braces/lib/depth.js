'use strict';

// Bound every recursive walker, including callers supplying an AST directly.
const MAX_DEPTH = 100;
const depthError = () => new SyntaxError(`Pattern nesting exceeds supported depth (${MAX_DEPTH})`);
const assertDepth = ast => {
  const pending = [{ node: ast, depth: 0 }];
  while (pending.length > 0) {
    const { node, depth } = pending.pop();
    if (!node.nodes) continue;
    if (depth > MAX_DEPTH) throw depthError();
    for (const child of node.nodes) pending.push({ node: child, depth: depth + 1 });
  }
};

module.exports = { MAX_DEPTH, depthError, assertDepth };
