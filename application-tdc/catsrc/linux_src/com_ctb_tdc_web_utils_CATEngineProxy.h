/* DO NOT EDIT THIS FILE - it is machine generated */
#include "jni.h"
/* Header for class com_ctb_tdc_web_utils_CATEngineProxy */

#ifndef _Included_com_ctb_tdc_web_utils_CATEngineProxy
#define _Included_com_ctb_tdc_web_utils_CATEngineProxy
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_ctb_tdc_web_utils_CATEngineProxy
 * Method:    setup_cat
 * Signature: (Ljava/lang/String)I
 */
JNIEXPORT jint JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_setup_1cat
  (JNIEnv *, jclass, jstring);

/*
 * Class:     com_ctb_tdc_web_utils_CATEngineProxy
 * Method:    next_item
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_next_1item
  (JNIEnv *, jclass);

/*
 * Class:     com_ctb_tdc_web_utils_CATEngineProxy
 * Method:    set_rwo
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_set_1rwo
  (JNIEnv *, jclass, jint);

/*
 * Class:     com_ctb_tdc_web_utils_CATEngineProxy
 * Method:    score
 * Signature: ()D
 */
JNIEXPORT jdouble JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_score
  (JNIEnv *, jclass);

/*
 * Class:     com_ctb_tdc_web_utils_CATEngineProxy
 * Method:    getSEM
 * Signature: (D)D
 */
JNIEXPORT jdouble JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_getSEM
  (JNIEnv *, jclass, jdouble);

JNIEXPORT jint JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_get_1nObj (JNIEnv *, jclass);
JNIEXPORT jint JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_get_1objID (JNIEnv *, jclass, jint);
/* JNIEXPORT jdouble JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_get_1objScore (JNIEnv *, jclass, jdouble, jint); */
/* JNIEXPORT jdouble JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_get_1objScaleScore (JNIEnv *, jclass, jchar, jint); */
JNIEXPORT jdouble JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_get_1objScaleScore (JNIEnv *, jclass, jint);

JNIEXPORT jdouble JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_get_1objSSsem (JNIEnv *, jclass, jdouble, jint);

JNIEXPORT jint JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_get_1totObjRS (JNIEnv *, jclass);
JNIEXPORT jint JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_get_1objRS (JNIEnv *, jclass);
/* JNIEXPORT jint JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_get_1objMasteryLvl (JNIEnv * env, jclass, jdouble, jint, jchar); */

JNIEXPORT jint JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_get_1objMasteryLvl (JNIEnv * env, jclass, jdouble, jint);
JNIEXPORT jint JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_getTestLength(JNIEnv *, jclass);
JNIEXPORT void JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_resumeCAT (JNIEnv *, jclass, jint, jintArray, jintArray);
/*
 * Class:     com_ctb_tdc_web_utils_CATEngineProxy
 * Method:    setoff_cat
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_ctb_tdc_web_utils_CATEngineProxy_setoff_1cat
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif
