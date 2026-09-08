#define __STDC_WANT_LIB_EXT1__ 1
#import <Foundation/Foundation.h>
#import <Security/Security.h>
#import <LocalAuthentication/LocalAuthentication.h>
#include <jni.h>
#include <string.h>

#define JNI_METHOD(name) Java_cn_mafei_cmp_platform_secure_1storage_1macos_NativeKeychain_##name
#define MAX_SECRET_BYTES (1024 * 1024)
#define MAX_ALIAS_CHARS (16 * 1024)

static void clearData(NSMutableData *data) {
    if (data != nil && data.length > 0) (void)memset_s(data.mutableBytes, data.length, 0, data.length);
}

// All JNI entrypoints are scoped, non-interactive SecItem operations. No access
// to unrelated services, default-keychain mutation, search-list changes, CLI,
// plaintext files, or native raw-pointer handles are exposed.
static NSString *fromJava(JNIEnv *env, jstring value, jsize maximum) {
    if (value == NULL) return nil;
    jsize length = (*env)->GetStringLength(env, value);
    if (length <= 0 || length > maximum) return nil;
    const jchar *characters = (*env)->GetStringChars(env, value, NULL);
    if (characters == NULL) return nil;
    NSString *result = [[NSString alloc] initWithCharacters:(const unichar *)characters length:(NSUInteger)length];
    (*env)->ReleaseStringChars(env, value, characters);
    return result;
}

static NSMutableDictionary *query(JNIEnv *env, jstring serviceValue, jstring accountValue, BOOL accountRequired) {
    NSString *service = fromJava(env, serviceValue, 550);
    NSString *servicePrefix = @"cn.mafei.credentials.v1/";
    if (service == nil || ![service hasPrefix:servicePrefix] || service.length <= servicePrefix.length) return nil;
    NSString *account = fromJava(env, accountValue, MAX_ALIAS_CHARS);
    if (accountRequired && account == nil) return nil;
    LAContext *authentication = [[LAContext alloc] init];
    authentication.interactionNotAllowed = YES;
    NSMutableDictionary *result = [@{
        (__bridge id)kSecClass: (__bridge id)kSecClassGenericPassword,
        (__bridge id)kSecAttrService: service,
        (__bridge id)kSecAttrSynchronizable: @NO,
        (__bridge id)kSecUseDataProtectionKeychain: @YES,
        (__bridge id)kSecUseAuthenticationContext: authentication,
    } mutableCopy];
    if (account != nil) result[(__bridge id)kSecAttrAccount] = account;
    return result;
}

static NSMutableData *fromJavaData(JNIEnv *env, jbyteArray input) {
    if (input == NULL) return nil;
    jsize length = (*env)->GetArrayLength(env, input);
    if (length < 9 || length > MAX_SECRET_BYTES) return nil;
    NSMutableData *data = [NSMutableData dataWithLength:(NSUInteger)length];
    if (data == nil) return nil;
    (*env)->GetByteArrayRegion(env, input, 0, length, (jbyte *)data.mutableBytes);
    if ((*env)->ExceptionCheck(env)) { clearData(data); return nil; }
    return data;
}

static jobject readResult(JNIEnv *env, OSStatus status, NSData *data) {
    jbyteArray bytes = NULL;
    if (status == errSecSuccess) {
        if (data == nil || data.length < 9 || data.length > MAX_SECRET_BYTES) status = errSecDecode;
        else {
            bytes = (*env)->NewByteArray(env, (jsize)data.length);
            if (bytes == NULL) return NULL;
            (*env)->SetByteArrayRegion(env, bytes, 0, (jsize)data.length, (const jbyte *)data.bytes);
            if ((*env)->ExceptionCheck(env)) return NULL;
        }
    }
    jclass type = (*env)->FindClass(env, "cn/mafei/cmp/platform/secure_storage_macos/KeychainRead");
    if (type == NULL) return NULL;
    jmethodID init = (*env)->GetMethodID(env, type, "<init>", "(I[B)V");
    if (init == NULL) return NULL;
    return (*env)->NewObject(env, type, init, (jint)status, bytes);
}

static jstring toJava(JNIEnv *env, NSString *value) {
    if (value.length == 0 || value.length > MAX_ALIAS_CHARS) return NULL;
    unichar *characters = calloc(value.length, sizeof(unichar));
    if (characters == NULL) return NULL;
    [value getCharacters:characters range:NSMakeRange(0, value.length)];
    jstring result = (*env)->NewString(env, (const jchar *)characters, (jsize)value.length);
    free(characters);
    return result;
}

static jobject aliasesResult(JNIEnv *env, OSStatus status, NSArray *items) {
    jobjectArray accounts = NULL;
    if (status == errSecSuccess) {
        if (items == nil || items.count > INT32_MAX) status = errSecDecode;
        else {
            jclass stringType = (*env)->FindClass(env, "java/lang/String");
            if (stringType == NULL) return NULL;
            accounts = (*env)->NewObjectArray(env, (jsize)items.count, stringType, NULL);
            if (accounts == NULL) return NULL;
            for (NSUInteger index = 0; index < items.count; index++) {
                if (![items[index] isKindOfClass:[NSDictionary class]]) { status = errSecDecode; accounts = NULL; break; }
                id account = items[index][(__bridge id)kSecAttrAccount];
                if (![account isKindOfClass:[NSString class]]) { status = errSecDecode; accounts = NULL; break; }
                jstring value = toJava(env, account);
                if (value == NULL) { status = errSecDecode; accounts = NULL; break; }
                (*env)->SetObjectArrayElement(env, accounts, (jsize)index, value);
                (*env)->DeleteLocalRef(env, value);
                if ((*env)->ExceptionCheck(env)) return NULL;
            }
        }
    }
    jclass type = (*env)->FindClass(env, "cn/mafei/cmp/platform/secure_storage_macos/KeychainAliases");
    if (type == NULL) return NULL;
    jmethodID init = (*env)->GetMethodID(env, type, "<init>", "(I[Ljava/lang/String;)V");
    if (init == NULL) return NULL;
    return (*env)->NewObject(env, type, init, (jint)status, accounts);
}

JNIEXPORT jint JNICALL JNI_METHOD(add)(JNIEnv *env, jobject receiver, jstring service, jstring account, jbyteArray input) {
    (void)receiver;
    @autoreleasepool {
        NSMutableDictionary *attributes = query(env, service, account, YES);
        NSMutableData *data = fromJavaData(env, input);
        if (attributes == nil || data == nil) { clearData(data); return errSecParam; }
        attributes[(__bridge id)kSecValueData] = data;
        attributes[(__bridge id)kSecAttrAccessible] = (__bridge id)kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly;
        OSStatus status = SecItemAdd((__bridge CFDictionaryRef)attributes, NULL);
        clearData(data);
        return status;
    }
}

JNIEXPORT jint JNICALL JNI_METHOD(update)(JNIEnv *env, jobject receiver, jstring service, jstring account, jbyteArray input) {
    (void)receiver;
    @autoreleasepool {
        NSMutableDictionary *attributes = query(env, service, account, YES);
        NSMutableData *data = fromJavaData(env, input);
        if (attributes == nil || data == nil) { clearData(data); return errSecParam; }
        NSDictionary *changes = @{(__bridge id)kSecValueData: data};
        OSStatus status = SecItemUpdate((__bridge CFDictionaryRef)attributes, (__bridge CFDictionaryRef)changes);
        clearData(data);
        return status;
    }
}

JNIEXPORT jobject JNICALL JNI_METHOD(read)(JNIEnv *env, jobject receiver, jstring service, jstring account) {
    (void)receiver;
    @autoreleasepool {
        NSMutableDictionary *attributes = query(env, service, account, YES);
        if (attributes == nil) return readResult(env, errSecParam, nil);
        attributes[(__bridge id)kSecReturnData] = @YES;
        attributes[(__bridge id)kSecMatchLimit] = (__bridge id)kSecMatchLimitOne;
        CFTypeRef rawResult = NULL;
        OSStatus status = SecItemCopyMatching((__bridge CFDictionaryRef)attributes, &rawResult);
        id result = CFBridgingRelease(rawResult);
        if (status == errSecSuccess && ![result isKindOfClass:[NSData class]]) status = errSecDecode;
        return readResult(env, status, status == errSecSuccess ? result : nil);
    }
}

JNIEXPORT jobject JNICALL JNI_METHOD(aliases)(JNIEnv *env, jobject receiver, jstring service) {
    (void)receiver;
    @autoreleasepool {
        NSMutableDictionary *attributes = query(env, service, NULL, NO);
        if (attributes == nil) return aliasesResult(env, errSecParam, nil);
        attributes[(__bridge id)kSecReturnAttributes] = @YES;
        attributes[(__bridge id)kSecMatchLimit] = (__bridge id)kSecMatchLimitAll;
        CFTypeRef rawResult = NULL;
        OSStatus status = SecItemCopyMatching((__bridge CFDictionaryRef)attributes, &rawResult);
        id result = CFBridgingRelease(rawResult);
        if (status == errSecSuccess && ![result isKindOfClass:[NSArray class]]) status = errSecDecode;
        return aliasesResult(env, status, status == errSecSuccess ? result : nil);
    }
}

JNIEXPORT jint JNICALL JNI_METHOD(delete)(JNIEnv *env, jobject receiver, jstring service, jstring account) {
    (void)receiver;
    @autoreleasepool {
        NSMutableDictionary *attributes = query(env, service, account, YES);
        return attributes == nil ? errSecParam : SecItemDelete((__bridge CFDictionaryRef)attributes);
    }
}
