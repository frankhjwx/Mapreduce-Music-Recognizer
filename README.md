# Mapreduce-Music-Recognizer
基于hadoop与Shazam算法的分布式音频识别系统
包含特征提取和音频识别两大模块

## 特征提取模块 FactorExtractor
内容包含两个MapReduce过程，第一个过程将大数据音频分配给多个机器进行特征提取(仅按音频数量进行分配，并未考虑音频长度)
第二个过程合并每个音乐提取到的特征结果，并按哈希值建立索引

## 音频识别模块 MusicRecognizer
对需要识别的音频提取特征并在特征库中进行哈希匹配得到识别结果

## 文件目录
MusicRec/input - 输入文件，包含标识需要提取特征音频文件名的单个文件，每个文件名以逗号隔开
MusicRec/data - 输入音频库，包含多个音频
MusicRec/questdata - 需要识别的音频(仅实现了识别单个音频)

中间结果目录
MusicRec/FactorData - 得到的特征文件
MusicRec/FactorExtractortmp - 中间结果
MusicRec/MusicRecognizertmp - 中间结果

最终结果目录
MusicRec/output - 包含单个文件，表示特征匹配top5的结果

## Usage
特征提取 hadoop jar FactorExtractor.jar
音频识别 hadoop jar MusicRecognizer.jar xxx.wav

##注意点
只能处理44100Hz 单声道 16bit 无标签的wav格式音频

##致谢
https://github.com/winston-wen/Shazam winston-wen的单机音频识别程序
