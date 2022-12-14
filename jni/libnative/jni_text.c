
/*
 * jni text functions
 *
 * last modified: 2015.09.18
 */

#include <stdint.h>
#include <sys/types.h>
#include <stddef.h>
#include <stdbool.h>
#include <string.h>
#include <ctype.h>
#include <stdio.h>

#include "common/common.h"
#include "common/functions.h"

// convert ascii string to lowercase
jstring PUBLIC NATIVE_FUNCTION(asciiToLower, nf81) (JNIEnv* env, jobject thiz,
													IN jstring str)
{
	char *str_l = NULL, *str_s = NULL;
	char *buf, tmp[8192];
	jint size;
	int i, is_tmp;

	if (str == NULL)
		return NULL;

	str_s = (char *) (*env)->GetStringChars(env, str, NULL);
	size = (*env)->GetStringLength(env, str);

	if (str_s == NULL)
	{
		E("GetStringChars failed"); // ?
		
	}
	else if (size > 0)
	{
		do
		{
			size *= sizeof(jchar); // XXX overflow
			if (size > sizeof(tmp))
			{
				// use malloc
				buf = (char *) malloc(size);
				if (buf == NULL)
				{
					I("malloc failed");
					break;
				}
				is_tmp = 0;
			}
			else
			{
				// use internal buffer
				buf = tmp;
				is_tmp = 1;
			}

			// tolower
			memset(buf, 0, size);
			for (i = 0; i < size; i += sizeof(jchar)) // XXX if jchar not BE ?
				buf[i] = (str_s[i] != EOF) ? tolower(str_s[i]) : str_s[i];
			(*env)->ReleaseStringChars(env, str, (jchar *) str_s);

			// new jstring
			str_l = (char *) (*env)->NewString(env, (jchar *) buf, size / sizeof(jchar));
			if (str_l == NULL)
				E("NewString failed"); // ?

			if (is_tmp == 0)
				free(buf);
		}
		while (0);
	}

	if (str_l == NULL)
		str_l = (char *) (*env)->NewString(env, (jchar *) "\0", 0);

	return (jstring) str_l;
}

/*
 * check if ascii string starts with another string
 *
 * "FOO".startsWith(""); == true
 */
jboolean PUBLIC NATIVE_FUNCTION(asciiStartsWith, nf82) (JNIEnv* env, jobject thiz,
														IN jstring start, IN jstring str)
{
	jboolean isStarts = JNI_FALSE;
	char *str_s = NULL;
	char *start_s = NULL;
	jsize str_size, start_size;

	if (start == NULL || str == NULL)
		return JNI_FALSE;

	start_s = (char *) (*env)->GetStringChars(env, start, NULL);
	start_size = (*env)->GetStringLength(env, start);
	str_s = (char *) (*env)->GetStringChars(env, str, NULL);
	str_size = (*env)->GetStringLength(env, str);

	if (start_s == NULL || str_s == NULL)
	{
		E("GetStringChars failed"); // ?
	}
	else if (start_size == 0 ||
				(start_size > 0 && start_size <= str_size &&
					memcmp(start_s, str_s, start_size * sizeof(jchar)) == 0)) // XXX overflow
	{
		isStarts = JNI_TRUE;
	}

	if (start_s != NULL)
		(*env)->ReleaseStringChars(env, start, (jchar *) start_s);
	if (str_s != NULL)
		(*env)->ReleaseStringChars(env, str, (jchar *) str_s);

	return isStarts;
}

/*
 * check if ascii string ends with another string
 *
 * "FOO".endsWith(""); == true
 */
jboolean PUBLIC NATIVE_FUNCTION(asciiEndsWith, nf83) (JNIEnv* env, jobject thiz,
														IN jstring end, IN jstring str)
{
	jboolean isEnds = JNI_FALSE;
	char *str_s = NULL;
	char *end_s = NULL;
	jsize str_size, end_size;

	if (end == NULL || str == NULL)
		return JNI_FALSE;

	end_s = (char *) (*env)->GetStringChars(env, end, NULL);
	end_size = (*env)->GetStringLength(env, end);
	str_s = (char *) (*env)->GetStringChars(env, str, NULL);
	str_size = (*env)->GetStringLength(env, str);

	if (end_s == NULL || str_s == NULL)
	{
		E("GetStringChars failed"); // ?
	}
	else if (end_size == 0 ||
				(end_size > 0 && end_size <= str_size &&
					memcmp(end_s, &str_s[(str_size - end_size) * sizeof(jchar)],
							end_size * sizeof(jchar)) == 0)) // XXX overflow
	{
		isEnds = JNI_TRUE;
	}

	if (end_s != NULL)
		(*env)->ReleaseStringChars(env, end, (jchar *) end_s);
	if (str_s != NULL)
		(*env)->ReleaseStringChars(env, str, (jchar *) str_s);

	return isEnds;
}

/*
 * return position of ascii string in another string
 *
 * pos = "F00".indexOf("F", 3); == -1
 * pos = "F00".indexOf(""); == 0
 * pos = "F00".indexOf("a", 5); == -1
 * pos = "F00".indexOf("", 5); == 3 !!!
 * pos = "F00".indexOf("", -1); == 0 !!!
 */
jint PUBLIC NATIVE_FUNCTION(asciiIndexOf, nf84) (JNIEnv* env, jobject thiz,
													IN jstring search, jint from, IN jstring str)
{
	jint pos = -1;
	char *str_s = NULL;
	char *search_s = NULL;
	char *s;
	jsize str_size, search_size;

	if (search == NULL || str == NULL)
		return JNI_FALSE;

	search_s = (char *) (*env)->GetStringChars(env, search, NULL);
	search_size = (*env)->GetStringLength(env, search);
	str_s = (char *) (*env)->GetStringChars(env, str, NULL);
	str_size = (*env)->GetStringLength(env, str);

	if (search_s == NULL || str_s == NULL)
	{
		E("GetStringChars failed"); // ?
	}
	else if (search_size == 0)
	{
		pos = (from < 0) ? 0 : ((from < str_size) ? from : str_size);
	}
	else if (search_size > 0 && search_size <= str_size - from && from >= 0 && from < str_size)
//				memcmp(end_s, &str_s[(str_size - end_size) * sizeof(jchar)],
//						end_size * sizeof(jchar)) == 0) // XXX overflow
	{
		s = (char *) memmem(&str_s[from * sizeof(jchar)], (str_size - from) * sizeof(jchar),
							search_s, search_size * sizeof(jchar));
		//E("memmem %p %d %p %d - %d %p", &str_s[from * sizeof(jchar)], str_size - from, search_s, search_size, from, s);
		if (s != NULL)
			pos = (s - str_s) / sizeof(jchar);
	}

	if (search_s != NULL)
		(*env)->ReleaseStringChars(env, search, (jchar *) search_s);
	if (str_s != NULL)
		(*env)->ReleaseStringChars(env, str, (jchar *) str_s);

	return pos;
}
