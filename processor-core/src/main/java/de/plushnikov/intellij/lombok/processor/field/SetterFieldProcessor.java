package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.Modifier;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.lombok.LombokConstants;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.lombok.util.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import de.plushnikov.intellij.lombok.util.PsiPrimitiveTypeFactory;
import lombok.Setter;
import lombok.core.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Setter lombok annotation on a field
 * Creates setter method for this field
 *
 * @author Plushnikov Michail
 */
public class SetterFieldProcessor extends AbstractLombokFieldProcessor {

  public SetterFieldProcessor() {
    this(Setter.class, PsiMethod.class);
  }

  protected SetterFieldProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<?> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      target.add((Psi) createSetterMethod(psiField, methodVisibility));
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result;
    result = validateFinalModifier(psiAnnotation, psiField, builder);
    if (result) {
      result = validateVisibility(psiAnnotation);
      if (result) {
        result = validateExistingMethods(psiField, builder);
      }
    }
    return result;
  }

  protected boolean validateFinalModifier(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiField.hasModifierProperty(PsiModifier.FINAL)) {
      builder.addError(String.format("'@%s' on final field is not allowed", psiAnnotation.getQualifiedName()),
          PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, false, false));
      result = false;
    }
    return result;
  }

  protected boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibity = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibity;
  }

  protected boolean validateExistingMethods(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      final PsiMethod[] classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
      final PsiType booleanType = PsiPrimitiveTypeFactory.getInstance().getBooleanType();
      final boolean isBoolean = booleanType.equals(psiField.getType());
      final Collection<String> methodNames = getAllSetterNames(psiField, isBoolean);

      for (String methodName : methodNames) {
        if (PsiMethodUtil.hasMethodByName(classMethods, methodName)) {
          final String setterMethodName = getSetterName(psiField, isBoolean);

          builder.addWarning(String.format("Not generated '%s'(): A method with similar name '%s' already exists", setterMethodName, methodName));
          result = false;
        }
      }
    }
    return result;
  }

  public List<String> getAllSetterNames(@NotNull PsiField psiField, boolean isBoolean) {
    return TransformationsUtil.toAllSetterNames(psiField.getName(), isBoolean);
  }

  protected String getSetterName(@NotNull PsiField psiField, boolean isBoolean) {
    return TransformationsUtil.toSetterName(psiField.getName(), isBoolean);
  }

  @NotNull
  public PsiMethod createSetterMethod(@NotNull PsiField psiField, @Modifier @NotNull String methodModifier) {
    final String fieldName = psiField.getName();
    final PsiType psiFieldType = psiField.getType();
    final PsiType booleanType = PsiPrimitiveTypeFactory.getInstance().getBooleanType();
    final String methodName = getSetterName(psiField, booleanType.equals(psiFieldType));

    PsiClass psiClass = psiField.getContainingClass();
    assert psiClass != null;

    UserMapKeys.addWriteUsageFor(psiField);

    LombokLightMethodBuilder method = LombokPsiElementFactory.getInstance().createLightMethod(psiField.getManager(), methodName)
        .withMethodReturnType(getReturnType(psiField))
        .withContainingClass(psiClass)
        .withParameter(fieldName, psiFieldType)
        .withNavigationElement(psiField);
    if (StringUtil.isNotEmpty(methodModifier)) {
      method.withModifier(methodModifier);
    }
    if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
      method.withModifier(PsiModifier.STATIC);
    }

    PsiParameter methodParameter = method.getParameterList().getParameters()[0];
    PsiModifierList methodParameterModifierList = methodParameter.getModifierList();
    if (null != methodParameterModifierList) {
      final Collection<String> annotationsToCopy = PsiAnnotationUtil.collectAnnotationsToCopy(psiField,
          LombokConstants.NON_NULL_PATTERN, LombokConstants.NULLABLE_PATTERN);
      for (String annotationFQN : annotationsToCopy) {
        methodParameterModifierList.addAnnotation(annotationFQN);
      }
    }

    return method;

  }

  protected PsiType getReturnType(@NotNull PsiField psiField) {
    return PsiPrimitiveTypeFactory.getInstance().getVoidType();
  }

}
