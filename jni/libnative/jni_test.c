
/*
 * jni test and debug functions
 *
 * last modified: 2015.09.18
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/inotify.h>

#include "common/common.h"
#include "common/functions.h"

// return version of native libraries
jlong PUBLIC NATIVE_FUNCTION(getLibsVersion, nf71) (JNIEnv* env, jobject thiz)
{
	return LIBS_VERSION;
}

//
jstring PUBLIC NATIVE_FUNCTION(getString, nf72) (JNIEnv* env, jobject thiz)
{
	char* str;
	char buf[128];

	asprintf(&str, "ln %s %s", __DATE__, __TIME__);
	strlcpy(buf, str, sizeof(buf));
	free(str);

	I("%s", buf);
	return (*env)->NewStringUTF(env, buf);
}

//
void PUBLIC NATIVE_FUNCTION(dumpProcs, nf73) (JNIEnv* env, jobject thiz)
{
	char buf[64], name[1][ANDROID_PKGNAME_MAX];
	unsigned int uid;
	DIR *proc_dir;
	struct dirent *dir_entry;
	struct stat dir_stat;

	proc_dir = opendir("/proc");
	if (proc_dir == NULL)
	{
		I("opendir error %d", errno);
		return;
	}

	while ((dir_entry = readdir(proc_dir)) != NULL)
	{
		if (dir_entry->d_type != DT_DIR || !is_int(dir_entry->d_name))
			continue;

		SNPRINTF(buf, "/proc/%s", dir_entry->d_name);
		uid = -1;
		if (stat(buf, &dir_stat) == -1)
			I("stat '%s' error %d", buf, errno);
		else
			uid = dir_stat.st_uid;

		name[0][0] = 0;
		if (uid2name(uid, name, 1) == 0)
			I("uid2name failed");

		E("proc %s uid %d name '%s'", dir_entry->d_name, uid, name[0]);
	}

	closedir(proc_dir);
	return;
}

/*
//
void PUBLIC NATIVE_FUNCTION(testNotify) (JNIEnv* env, jobject thiz)
{
    int inotify_fd, wd, i = 0;
    ssize_t num;
    char buf[512];
    char path[] = "/data";
//    char path[] = "/data/local/tmp";

    I("start inotify on '%s'", path);
    inotify_fd = inotify_init(); // Create inotify instance
    if (inotify_fd == -1)
    {
        E("inotify_init err (%d)", errno);
	return;
    }

    wd = inotify_add_watch(inotify_fd, path, IN_CREATE | IN_DONT_FOLLOW);
//    wd = inotify_add_watch(inotify_fd, path, IN_ALL_EVENTS);
    if (wd == -1)
    {
        E("inotify_add_watch err (%d)", errno);
	return;
    }

    I("wait 3th events");
    while (i < 3)
    {
	num = read(inotify_fd, buf, sizeof(buf));
	if (num == 0 || num == -1)
	{
	    E("inotify read err (%d)", (int) num);
	}

	I("event!");
	i++;
    }
}
*/

//
void PUBLIC NATIVE_FUNCTION(testNetlink, nf74) (JNIEnv* env, jobject thiz)
{
	int retval, s, fd = 0;
	uint32_t sq;
	void *h;
	uint8_t buf[8192];
	struct inet_diag_msg *r;

	while ((retval = netlink_diag_get_tcpsock(buf, sizeof(buf), &fd, &sq, &h, &s, &r)) > 0)
	{
		// this code does not (need to) distinguish between IPv4 and IPv6
		E("%d: %d -> %d %d", r->idiag_uid, ntohs(r->id.idiag_sport),
			ntohs(r->id.idiag_dport), r->idiag_state);
	}

	if (retval < 0)
		I("netlink error %d", retval);
}
