package me.lucko.spark.fabric.mixin;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.sugar.Local;
import me.lucko.spark.common.sampler.AbstractSampler;
import me.lucko.spark.common.sampler.aggregator.DataAggregator;
import me.lucko.spark.common.sampler.node.AbstractNode;
import me.lucko.spark.common.sampler.node.StackTraceNode;
import me.lucko.spark.common.sampler.node.ThreadNode;
import me.lucko.spark.common.sampler.node.exporter.NodeExporter;
import me.lucko.spark.common.sampler.source.ClassSourceLookup;
import me.lucko.spark.common.sampler.window.ProtoTimeEncoder;
import me.lucko.spark.common.util.classfinder.ClassFinder;
import me.lucko.spark.fabric.deobfuscator.StackTraceDeobfuscator;
import me.lucko.spark.proto.SparkSamplerProtos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(AbstractSampler.class)
public class AbstractSamplerMixin {
    @Inject(method = "writeDataToProto", at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V", shift = At.Shift.AFTER), remap = false)
    public void remap(SparkSamplerProtos.SamplerData.Builder proto, DataAggregator dataAggregator, Function<ProtoTimeEncoder, NodeExporter> nodeExporterFunction, ClassSourceLookup classSourceLookup, Supplier<ClassFinder> classFinderSupplier, CallbackInfo ci, @Local List<ThreadNode> data){
        data.forEach(AbstractSamplerMixin::updateNodeChildrenInformation);
    }

    @Unique
    private static void updateNodeChildrenInformation(AbstractNode node)
    {
        List<StackTraceNode> children = Lists.newArrayList(node.getChildren());
        node.getChildren().clear();
        children.forEach(child -> {
            updateNodeChildrenInformation(child);
            StackTraceNode.Description description = updateDescription(child);
            node.children.put(description, child);
        });
    }

    @Unique
    private static StackTraceNode.Description updateDescription(StackTraceNode node)
    {

        if(node.description instanceof StackTraceNode.AsyncDescription){
            StackTraceNode.AsyncDescription description;
            description = new StackTraceNode.AsyncDescription(
                    StackTraceDeobfuscator.remapClass(node.getClassName()).orElse(node.getClassName()),
                    StackTraceDeobfuscator.remapMethod(node.getMethodName()).orElse(node.getMethodName()),
                    node.getMethodDescription()
            );
            node.description = description;
            return description;
        }else if(node.description instanceof StackTraceNode.JavaDescription){
            StackTraceNode.JavaDescription description;
            description = new StackTraceNode.JavaDescription(
                    StackTraceDeobfuscator.remapClass(node.getClassName()).orElse(node.getClassName()),
                    StackTraceDeobfuscator.remapMethod(node.getMethodName()).orElse(node.getMethodName()),
                    node.getLineNumber(),
                    node.getParentLineNumber()
            );
            node.description = description;
            return description;
        }
        return node.description;
    }
}
