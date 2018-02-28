import hash.CombineHash;
import hash.FFT;
import hash.Hash;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.IOUtils;
import pcm.PCM16MonoData;
import pcm.PCM16MonoParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.TreeSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.lib.input.LineRecordReader;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.partition.HashPartitioner;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;

public class FactorExtractor {
    public static String systempath = "hdfs://master01:9000/user/2017st25/";

    public static class FactorExtractorMap extends Mapper<Object,Text,Text,Text>{
        private Text map_value = new Text();
        private Text map_key = new Text();

        public void map(Object key, Text value,Context context)
                throws IOException, InterruptedException {
            String[] filenames = value.toString().split(",");
            for (int i=0; i<filenames.length; i++) {
                map_value.set(String.valueOf(i));
                map_key.set(filenames[i]);
                context.write(map_key, new Text(String.valueOf(String.valueOf(i))));
            }
        }
    }


    public static class FactorExtractorReduce extends Reducer<Text,Text,Text,Text>{
        private Text result = new Text();

        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            for (Text value : values) {
                String s=key.toString();
                if (s.contains(".wav")) {
                    String uri = systempath + "MusicRec/data";
                    Configuration conf = new Configuration();
                    FileSystem fs = FileSystem.get(URI.create(uri), conf);
                    FSDataInputStream in = null;
                    in = fs.open(new Path(uri + "/" + s));

                    ArrayList<Hash> hashes = CombineHash.generateFingerprint(in, Integer.valueOf(value.toString()));
                    for (int k=0; k<hashes.size(); k++)
                        context.write(new Text(String.valueOf(hashes.get(k).getHashID())),
                                new Text(String.valueOf(hashes.get(k).offset)
                                        +"#"+s
                                        +"#"+value.toString()));
                }
            }
        }
    }
    public static class HashCombinerMap extends Mapper<Object,Text,Text,Text> {
        private Text map_value = new Text();
        private Text map_key = new Text();

        public void map(Object key, Text value,Context context)
                throws IOException, InterruptedException {
            String[] s = value.toString().split("\t");
            context.write(new Text(s[0]), new Text(s[1]));
        }
    }

    public static class HashCombinerReduce extends Reducer<Text,Text,Text,Text> {
        private Text result = new Text();

        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            String s = "";

            for (Text value : values){
                s = s + value.toString() + "@";
            }
            s = s.substring(0,s.length()-1);
            context.write(key, new Text(s));
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {

        Configuration conf1 = new Configuration();

        Job job1 = new Job(conf1,"FactorExtractor");

        job1.setJarByClass(FactorExtractor.class);

        job1.setMapperClass(FactorExtractorMap.class);
        job1.setMapOutputKeyClass(Text.class);
        job1.setMapOutputValueClass(Text.class);

        job1.setReducerClass(FactorExtractorReduce.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job1, new Path(systempath + "/MusicRec/input"));
        FileOutputFormat.setOutputPath(job1, new Path(systempath + "/MusicRec/FactorExtractortmp"));
        job1.waitForCompletion(true);

        Configuration conf2 = new Configuration();

        Job job2 = new Job(conf2,"HashCombiner");

        job2.setJarByClass(FactorExtractor.class);

        job2.setMapperClass(HashCombinerMap.class);
        job2.setMapOutputKeyClass(Text.class);
        job2.setMapOutputValueClass(Text.class);

        job2.setReducerClass(HashCombinerReduce.class);
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job2, new Path(systempath + "MusicRec/FactorExtractortmp"));
        FileOutputFormat.setOutputPath(job2, new Path(systempath + "MusicRec/FactorData"));
        job2.waitForCompletion(true);

        System.exit(job2.waitForCompletion(true)?0:1);

    }
}

