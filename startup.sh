#! /bin/sh
BASE_PATH='./'
CONTAINER_NAME=''
PROJECT_PATH=''
PORT=8080
while getopts "c:d:p:" opt; do
    case $opt in
        c)
            CONTAINER_NAME=$OPTARG
          ;;
         d)
            PROJECT_PATH=$OPTARG
          ;;
         p)
            PORT=$OPTARG
          ;;
         ?)
            echo "unkonw argument"
            exit 1
          ;;
    esac
done
PROJECT_NAME="${CONTAINER_NAME}-service"
IMAGE_NAME="${CONTAINER_NAME}:latest"
echo "copy app.jar from ${BASE_PATH}/${PROJECT_PATH}"
rm -f app.jar
cp ${BASE_PATH}/${PROJECT_PATH}/target/${PROJECT_NAME}.jar ./app.jar || (echo "build failed, path not exists :(" && exit )

echo "begin to build ${PROJECT_NAME} image ！！"

[ -n "`docker ps | grep ${CONTAINER_NAME}`" ] && docker rm -f ${CONTAINER_NAME}
[ -n "`docker images | grep ${CONTAINER_NAME}`" ] && docker rmi ${IMAGE_NAME}

docker build -t ${IMAGE_NAME} .
echo "${PROJECT_NAME} image build success ！！^_^"

echo "begin to create container ${CONTAINER_NAME}，port: ${PORT} ！！"

docker run -d --name ${CONTAINER_NAME} \
 -e JAVA_OPTS="-Xms256m -Xmx256m" \
 -p "${PORT}:${PORT}" \
 --network heima-net ${IMAGE_NAME} \
&& echo "container is running now !! ^_^"