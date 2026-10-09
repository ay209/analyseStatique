package hai913i.tp1.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.FactsInvariants;
import hai913i.tp1.TestSupport;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.parse.JdtParser.ParsedFile;

/**
 * Encodage et fins de ligne. DECISION : l'analyseur est tolerant. Un fichier qui n'est pas de l'UTF-8 valide ne doit
 * ni faire planter l'analyse ni fausser le nombre de lignes (une ligne se termine par \n, \r ou \r\n).
 */
class EncodingTest {

    private static Path put(Path root, String relative, byte[] bytes) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.write(file, bytes);
    }

    private static int lines(Path root, String content) throws IOException {
        put(root, "p/X.java", content.getBytes(StandardCharsets.UTF_8));
        return TestSupport.facts(root).totalLines();
    }

    @Test
    void iso88591FileIsAnalysedWithoutCrash(@TempDir Path tmp) throws IOException {
        put(tmp, "p/Latin.java", "package p;\n// caf\u00e9 \u00e0 la cr\u00e8me\nclass Latin { String s = \"\u00e9t\u00e9\"; void f() { } }\n"
                .getBytes(StandardCharsets.ISO_8859_1));
        ProjectFacts f = TestSupport.facts(tmp);
        assertEquals(1, f.types().size());
        assertEquals(3, f.totalLines());
        assertEquals(1, TestSupport.type(f, "p.Latin").methods().size());
        FactsInvariants.checkStrict(f);
    }

    @Test
    void invalidUtf8BytesInsideAStringLiteralDoNotBreakTheLineCount(@TempDir Path tmp) throws IOException {
        byte[] head = "package p;\nclass A {\n  String s = \"".getBytes(StandardCharsets.UTF_8);
        byte[] tail = "\";\n}\n".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[head.length + 3 + tail.length];
        System.arraycopy(head, 0, bytes, 0, head.length);
        bytes[head.length] = (byte) 0xC3;      // debut de sequence UTF-8 interrompu
        bytes[head.length + 1] = (byte) 0xFF;  // octet jamais valide en UTF-8
        bytes[head.length + 2] = (byte) 0xE9;
        System.arraycopy(tail, 0, bytes, head.length + 3, tail.length);
        put(tmp, "p/A.java", bytes);
        ProjectFacts f = TestSupport.facts(tmp);
        assertEquals(4, f.totalLines());
        assertEquals(1, f.types().size());
    }

    @Test
    void utf8WithBomIsParsedAndCountedNormally(@TempDir Path tmp) throws IOException {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "package p;\nclass B { void f() { } }\n".getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        put(tmp, "p/B.java", all);
        List<ParsedFile> parsed = TestSupport.parse(tmp);
        assertEquals(0, Arrays.stream(parsed.get(0).unit().getProblems()).filter(p -> p.isError()).count(),
                Arrays.toString(parsed.get(0).unit().getProblems()));
        ProjectFacts f = TestSupport.extract(parsed);
        assertEquals(2, f.totalLines());
        assertEquals("p.B", f.types().get(0).qualifiedName());
    }

    @Test
    void multiByteUtf8CharactersDoNotChangeTheLineCount(@TempDir Path tmp) throws IOException {
        assertEquals(3, lines(tmp, "package p;\n// \u00e9\u00e8\u00ea \u20ac \ud83d\ude00 \u4e2d\u6587\nclass X { }\n"));
    }

    @Test
    void lineTerminatorsAreCountedLikeReadAllLines(@TempDir Path tmp) throws IOException {
        String[][] cases = {
            {"", "0"},
            {"\n", "1"},
            {"a", "1"},
            {"a\n", "1"},
            {"a\n\n", "2"},
            {"a\r\nb", "2"},
            {"a\r\nb\r\n", "2"},
            {"a\rb\r", "2"},
            {"a\rb", "2"},
            {"\r\n\r\n", "2"},
            {"a\n\rb", "3"},
            {"a\r\n\r\nb\n", "3"},
        };
        int n = 0;
        for (String[] c : cases) {
            Path dir = tmp.resolve("c" + n++);
            assertEquals(Integer.parseInt(c[1]), lines(dir, c[0]),
                    "contenu " + c[0].replace("\r", "\\r").replace("\n", "\\n"));
        }
    }

    @Test
    void countMatchesReadAllLinesOnValidUtf8(@TempDir Path tmp) throws IOException {
        String[] samples = {"class X { }", "class X {\n}\n", "class X {\r\n}\r\n", "\n\n\nclass X { }\n\n", "// \u00e9\nclass X { }"};
        int n = 0;
        for (String s : samples) {
            Path dir = tmp.resolve("v" + n++);
            Path file = put(dir, "p/X.java", s.getBytes(StandardCharsets.UTF_8));
            assertEquals(Files.readAllLines(file).size(), TestSupport.facts(dir).totalLines(), s);
        }
    }

    @Test
    void sourceFileWithOnlyWhitespaceOrCommentsHasNoTypes(@TempDir Path tmp) throws IOException {
        put(tmp, "p/Empty.java", new byte[0]);
        put(tmp, "p/Spaces.java", "   \n\n\t\n".getBytes(StandardCharsets.UTF_8));
        put(tmp, "p/Comment.java", "// rien\n/* ici */\n".getBytes(StandardCharsets.UTF_8));
        ProjectFacts f = TestSupport.facts(tmp);
        assertTrue(f.types().isEmpty());
        assertEquals(3, f.sourceFiles().size());
        assertEquals(0 + 3 + 2, f.totalLines());
    }
}
