# HarmonyOS thin host

This directory follows the CPF-KMP-CMP 1.0.0 host shape. Generated libkn.so
and libkn_api.h are published by:

    ./gradlew :composeApp:publishDebugBinariesToHarmonyApp

Then run ohpm install and Hvigor assembleHap inside this directory. Only
arm64-v8a is part of the MaFei release matrix.
