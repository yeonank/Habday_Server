#!/bin/bash
BUILD_WAR=$(ls /home/ec2-user/action/*.war)
WAR_NAME=$(basename $BUILD_WAR)
echo "> build 파일명: $WAR_NAME" >> /home/ec2-user/action/deploy.log

#echo "> build 파일 복사" >> /home/ec2-user/action/deploy.log
DEPLOY_PATH=/home/ec2-user/action/
#cp $BUILD_WAR $DEPLOY_PATH

echo "> 현재 실행중인 애플리케이션 pid 확인" >> /home/ec2-user/action/deploy.log
CURRENT_PID=$(pgrep -f $WAR_NAME)

echo ">pid: $CURRENT_PID"
if [ -z $CURRENT_PID ]
then
  echo "> 현재 구동중인 애플리케이션이 없으므로 종료하지 않습니다." >> /home/ec2-user/action/deploy.log
else
  echo "> kill -15 $CURRENT_PID"
  kill -15 $CURRENT_PID
  sleep 5
fi

DEPLOY_JAR=$DEPLOY_PATH$WAR_NAME
echo "> DEPLOY_JAR : $DEPLOY_JAR"
echo "> DEPLOY_JAR 배포"    >> /home/ec2-user/action/deploy.log
nohup java -jar $DEPLOY_JAR >> /home/ec2-user/deploy.log 2>/home/ec2-user/action/deploy_err.log &