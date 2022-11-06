package io.github.lgp547.anydoorplugin.action;

import com.google.gson.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.jvm.JvmNamedElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import io.github.lgp547.anydoorplugin.util.HttpUtil;
import io.github.lgp547.anydoorplugin.util.NotifierUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@NonNls
public class AnyDoorIntention extends PsiElementBaseIntentionAction implements IntentionAction {

    @NotNull
    public String getText() {
        return "Open any door";
    }

    @NotNull
    public String getFamilyName() {
        return "Any door";
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
        if (element == null) {
            return false;
        }

        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        return method != null;
    }

    public boolean startInWriteAction() {
        return false;
    }

    @SuppressWarnings({"StreamToLoop", "UnstableApiUsage"})
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (null == method) {
            throw new IncorrectOperationException("method is null");
        }

        String className = ((PsiClass) method.getParent()).getQualifiedName();
        String methodName = method.getName();
        List<String> paramTypeNameList = toParamTypeNameList(method.getParameterList());

        AnyDoorSettingsState service = project.getService(AnyDoorSettingsState.class);
        Integer port = project.getService(AnyDoorSettingsState.class).port;
        Consumer<Exception> openExcConsumer = e -> NotifierUtil.notifyError(project, "call any_door error " + e.getMessage());


        if (paramTypeNameList.isEmpty()) {
            openAnyDoor(className, methodName, paramTypeNameList, "{}", port, openExcConsumer);
        } else {

            String initContent;
            String cacheKey = getCacheKey(className, methodName, paramTypeNameList);
            String cache = service.getCache(cacheKey);
            if (cache != null) {
                initContent = cache;
            } else {
                // 生成默认的 json
                List<String> parameterNames = Arrays.stream(method.getParameters()).map(JvmNamedElement::getName).collect(Collectors.toList());
                JsonObject jsonObject = new JsonObject();
                parameterNames.forEach(name -> jsonObject.add(name, JsonNull.INSTANCE));
                Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
                initContent = gson.toJson(jsonObject);
            }

            TextAreaDialog dialog = new TextAreaDialog(project, "Generate call code", initContent);
            dialog.setOkAction(() -> {
                service.putCache(cacheKey, dialog.getText());
                openAnyDoor(className, methodName, paramTypeNameList, dialog.getText(), port, openExcConsumer);
            });
            dialog.show();
        }
    }

    private static void openAnyDoor(String className, String methodName,
                                    List<String> paramTypeNameList, String content, Integer port, Consumer<Exception> errHandle) {
        JsonObject jsonObjectReq = new JsonObject();
        jsonObjectReq.addProperty("content", content);
        jsonObjectReq.addProperty("methodName", methodName);
        jsonObjectReq.addProperty("className", className);
        jsonObjectReq.add("parameterTypes", toJsonArray(paramTypeNameList));

        HttpUtil.postAsy("http://127.0.0.1:" + port + "/any_door/run", jsonObjectReq.toString(), errHandle);
    }

    private static String getCacheKey(String className, String methodName, List<String> paramTypeNameList) {
        return className + "#" + methodName + "#" + String.join(",", paramTypeNameList);
    }

    private static List<String> toParamTypeNameList(PsiParameterList parameterList) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < parameterList.getParametersCount(); i++) {
            PsiParameter parameter = Objects.requireNonNull(parameterList.getParameter(i));
            String canonicalText = parameter.getType().getCanonicalText();
            list.add(canonicalText);
        }
        return list;
    }

    private static JsonArray toJsonArray(List<String> list) {
        JsonArray jsonArray = new JsonArray();
        list.forEach(jsonArray::add);
        return jsonArray;
    }

}