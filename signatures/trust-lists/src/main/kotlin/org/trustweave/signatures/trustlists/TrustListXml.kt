package org.trustweave.signatures.trustlists

import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Bounds and non-recursive traversal for untrusted trust-list XML.
 *
 * A hostile document can nest elements tens of thousands deep. A recursive DOM walk then dies with a
 * `StackOverflowError` (an `Error`, which ordinary exception handling does not see), so every walk here is iterative
 * and the document is refused outright beyond [MAX_DEPTH] or [MAX_INPUT_BYTES].
 */
internal object TrustListXml {
    /** Largest trust-list document accepted. The real EU lists are a few MiB at most. */
    const val MAX_INPUT_BYTES = 32 * 1024 * 1024

    /** Deepest element nesting accepted. Real trust lists, signature included, stay below 40. */
    const val MAX_DEPTH = 200

    /** A description of why [bytes] must not be parsed, or `null` when its size is acceptable. */
    fun sizeProblem(bytes: ByteArray): String? =
        if (bytes.size > MAX_INPUT_BYTES) "document is ${bytes.size} bytes, more than the $MAX_INPUT_BYTES limit" else null

    /** Whether any element of the tree under [root] is nested more than [MAX_DEPTH] levels below it. */
    fun exceedsDepth(root: Node): Boolean {
        val nodes = ArrayDeque<Node>()
        val depths = ArrayDeque<Int>()
        nodes.addLast(root)
        depths.addLast(1)
        while (nodes.isNotEmpty()) {
            val node = nodes.removeLast()
            val depth = depths.removeLast()
            if (depth > MAX_DEPTH) return true
            var child = node.firstChild
            while (child != null) {
                if (child is Element) {
                    nodes.addLast(child)
                    depths.addLast(depth + 1)
                }
                child = child.nextSibling
            }
        }
        return false
    }

    /** Asks the parser itself to stop at [MAX_DEPTH], where it supports the JDK limit; the post-parse check backs it. */
    fun limitDepth(factory: javax.xml.parsers.DocumentBuilderFactory) {
        try {
            factory.setAttribute("jdk.xml.maxElementDepth", MAX_DEPTH.toString())
        } catch (_: IllegalArgumentException) {
            // A parser without the JDK limit: exceedsDepth() still bounds what is accepted.
        }
    }

    /** Pre-order walk of every element at or under [root], without recursion. */
    inline fun forEachElement(
        root: Node,
        action: (Element) -> Unit,
    ) {
        val pending = ArrayDeque<Node>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (node is Element) action(node)
            var child = node.lastChild
            while (child != null) {
                pending.addLast(child)
                child = child.previousSibling
            }
        }
    }
}
