
/*
 * jni functions to work with files and disk
 *
 * last modified: 2015.09.18
 */

#include <stdbool.h>
#include <stdio.h>
#include <errno.h>
#include <fcntl.h>

#include "common/common.h"
//#include "external/sha1.h"

/*
 * set file fd to blocking/non-blocking state
 */
void PUBLIC NATIVE_FUNCTION(fileSetBlocking, nf10) (JNIEnv* env, jobject thiz,
													IN jint fd, IN jboolean enable)
{
	int flags;

	flags = fcntl(fd, F_GETFL);
	if (enable == JNI_TRUE)
	{
		// clear O_NONBLOCK and add O_DIRECT O_NOATIME
		flags &= (~O_NONBLOCK);
		flags &= (O_DIRECT | O_NOATIME);
	}
	else
	{
		flags &= (~(O_DIRECT | O_NOATIME));
		flags &= (O_NONBLOCK);
	}

	if (fcntl(fd, F_SETFL, flags) == -1)
		E("fcntl error %d", errno);
}

/*
 * close file fd, returns zero on success
 */
jint PUBLIC NATIVE_FUNCTION(fileClose, nf11) (JNIEnv* env, jobject thiz,
												IN jint fd)
{
	return close(fd);
}

/*
 * calculate sha1 from file, return byte[20] on success or null on error
 */
/*
jbyteArray PUBLIC NATIVE_FUNCTION(sha1File) (JNIEnv* env, jobject thiz,
												IN jstring filepath)
{
	bool iserr = true;
	sha1_ctx_t context;
	uint8_t digest[SHA1_HASH_BYTES];
	const char *filepath_s = NULL;
	FILE *f = NULL;
	int readed;
	uint8_t buf[4096]; // must be aligned to 512-bit (64 byte) block
	jbyteArray arr;

	if (filepath == NULL)
		return NULL;

	filepath_s = (*env)->GetStringUTFChars(env, filepath, NULL);
	if (filepath_s == NULL)
	{
		E("GetStringUTFChars failed"); // OutOfMemoryError already thrown
		return NULL;
	}

	f = fopen(filepath_s, "rb");
	if (f == NULL)
		goto sha1File_exit;

	// calc hash
	sha1_init(&context);
	while ((readed = fread(buf, 1, sizeof(buf), f)) > 0)
	{
		if (readed == sizeof(buf))
			sha1_nextBlocks(&context, buf, sizeof(buf) / SHA1_BLOCK_BYTES);
		else
			sha1_lastBlocks(&context, buf, readed);
	}
	sha1_ctx2hash(digest, &context);

	if (readed == 0 && feof(f) != 0)
		iserr = false;

sha1File_exit:

	if (f != NULL)
		fclose(f);
	(*env)->ReleaseStringUTFChars(env, filepath, filepath_s);

	if (iserr)
		return NULL;

	// return hash
	arr = (*env)->NewByteArray(env, SHA1_HASH_BYTES);
	if (arr == NULL)
		E("NewByteArray failed"); // ???
	else
		(*env)->SetByteArrayRegion(env, arr, 0, SHA1_HASH_BYTES, (jbyte *) digest);

	return arr;
}
*/
