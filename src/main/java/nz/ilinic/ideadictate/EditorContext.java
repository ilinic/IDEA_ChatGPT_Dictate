package nz.ilinic.ideadictate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.List;

/** Read this snapshot under IntelliJ's read lock. */
final class EditorContext {
    private EditorContext() { }

    static String capture(Project project) {
        StringBuilder result = new StringBuilder();
        result.append("PROJECT\n");
        result.append("  Name: ").append(project.getName()).append('\n');
        result.append("  Base directory: ").append(project.getBasePath()).append("\n\n");

        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            result.append("EDITOR\n  No selected text editor.\n");
            return result.toString();
        }

        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        int offset = editor.getCaretModel().getOffset();
        result.append("EDITOR\n");
        result.append("  File: ").append(file == null || file.getVirtualFile() == null
                ? "unknown" : file.getVirtualFile().getPath()).append('\n');
        result.append("  Caret: line ").append(editor.getCaretModel().getLogicalPosition().line + 1)
                .append(", column ").append(editor.getCaretModel().getLogicalPosition().column + 1).append('\n');
        String selection = editor.getSelectionModel().getSelectedText();
        result.append("  Selection: ").append(selection == null ? "none" : selection.length() + " chars").append('\n');
        if (selection != null) {
            result.append("  Selection preview: ").append(preview(selection, 180)).append('\n');
        }

        if (file == null || file.getTextLength() == 0) {
            result.append("  PSI: no PSI available\n");
            return result.toString();
        }

        PsiElement element = file.findElementAt(Math.min(offset, file.getTextLength() - 1));
        List<String> names = new ArrayList<>();
        for (PsiElement current = element; current != null && current != file; current = current.getParent()) {
            if (current instanceof PsiNamedElement named && named.getName() != null) {
                names.add(current.getClass().getSimpleName() + " = " + named.getName());
                if (names.size() >= 8) break;
            }
        }
        result.append("  Enclosing named PSI elements (inner first): ")
                .append(names.isEmpty() ? "none" : String.join(" -> ", names)).append('\n');

        // Java-specific PSI is guaranteed by the declared com.intellij.java dependency.
        com.intellij.psi.PsiMethod method = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiMethod.class);
        com.intellij.psi.PsiClass psiClass = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiClass.class);
        result.append("  Java class: ").append(psiClass == null ? "not applicable" : psiClass.getQualifiedName()).append('\n');
        result.append("  Java method: ").append(method == null ? "not applicable" : method.getName()).append('\n');
        return result.toString();
    }

    private static String preview(String value, int length) {
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= length ? singleLine : singleLine.substring(0, length) + "…";
    }
}

