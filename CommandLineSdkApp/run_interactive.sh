#!/bin/bash

logPath=$PWD/CommandLineSdkApp.log

./gradlew --console plain :CommandLineSdkApp:distTar  >${logPath} 2>&1
exitCode=$?
if [ ! ${exitCode} -eq 0 ]; then
    echo "*******************************************************"
    echo "ERROR running gradlew. last 50 lines of output:"
    echo "*******************************************************"
    tail -n 50 ${logPath}
    echo "*******************************************************"
    echo "ERROR building. See ${logPath} for more"
    echo "or just run './gradlew :CommandLineSdkApp:distTar' yourself"
    exit 1
fi
echo "---------------^^^^^ gradle ^^^^^----------------------" >>${logPath}
echo "---------------vvvvv  tar   vvvvv----------------------" >>${logPath}

# Find the most recently build tar file
# Usually, you shouldn't parse the output of ls.... but because mac's find sucks and doesn't
# include -printf we'll have to do it this way
distDir=CommandLineSdkApp/build/distributions/
tarPath=`ls -1tbd ${distDir}/*.tar* | head -1`
echo tarPath is $tarPath

tmpDir=`mktemp -d`
tar -xvf $tarPath -C $tmpDir >>${logPath} 2>&1
pushd $tmpDir/CommandLineSdkApp-*/ >/dev/null

echo "---------------^^^^^  tar   ^^^^^----------------------" >>${logPath}
echo "---------------vvvvv  app   vvvvv----------------------" >>${logPath}
echo
./bin/CommandLineSdkApp "$@" 2>>${logPath}
exitCode=$?
if [ ! ${exitCode} -eq 0 ]; then
    echo "*********************************************************"
    echo "ERROR running CommandLineSdkApp. last 15 lines of stderr:"
    echo "*********************************************************"
    tail -n 15 ${logPath}
    echo "*********************************************************"
    echo "ERROR. See ${logPath} for more"
fi

#./bin/CommandLineSdkApp "$@"
popd >/dev/null
rm -rf $tmpDir

