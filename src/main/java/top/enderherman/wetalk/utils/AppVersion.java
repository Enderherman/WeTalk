package top.enderherman.wetalk.utils;

import top.enderherman.wetalk.exception.BusinessException;

/** Numeric major/minor/patch ordering shared by release creation and update checks. */
public final class AppVersion {
    private AppVersion() {}

    public static boolean isValid(String value) {
        return value != null && value.length() <= 10 && value.matches("[0-9]+\\.[0-9]+\\.[0-9]+");
    }

    public static void validate(String value) {
        if (!isValid(value)) throw new BusinessException("版本号须为三段数字，且不能超过 10 个字符");
    }

    public static int compare(String first, String second) {
        validate(first);
        validate(second);
        String[] left = first.split("\\.");
        String[] right = second.split("\\.");
        for (int index = 0; index < 3; index++) {
            int comparison = Integer.compare(Integer.parseInt(left[index]), Integer.parseInt(right[index]));
            if (comparison != 0) return comparison;
        }
        return 0;
    }
}
