/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.remote

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableListParceler
import me.zhanghai.android.files.util.RemoteCallback
import me.zhanghai.android.files.util.getArgs
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.readParcelable

class ParcelablePathAttributesListConsumer(
    val value: (List<Pair<Path, BasicFileAttributes>>) -> Unit
) : Parcelable {
    private constructor(source: Parcel) : this(
        source.readParcelable<RemoteCallback>()!!.let { remoteCallback ->
            { pairs: List<Pair<Path, BasicFileAttributes>> ->
                val paths = pairs.map { it.first }
                val attributes = pairs.map { it.second.toParcelable() }
                remoteCallback.sendResult(Bundle().putArgs(ListenerArgs(paths, attributes)))
            }
        }
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(
            RemoteCallback { bundle ->
                val args = bundle.getArgs<ListenerArgs>()
                val pairs = args.paths.zip(args.attributes.map { it.value<BasicFileAttributes>() })
                value(pairs)
            },
            flags
        )
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<ParcelablePathAttributesListConsumer> {
            override fun createFromParcel(source: Parcel): ParcelablePathAttributesListConsumer =
                ParcelablePathAttributesListConsumer(source)

            override fun newArray(size: Int): Array<ParcelablePathAttributesListConsumer?> =
                arrayOfNulls(size)
        }
    }

    @Parcelize
    private class ListenerArgs(
        val paths: @WriteWith<ParcelableListParceler> List<Path>,
        val attributes: @WriteWith<ParcelableListParceler> List<ParcelableObject>
    ) : ParcelableArgs
}

fun ((List<Pair<Path, BasicFileAttributes>>) -> Unit).toParcelable(): ParcelablePathAttributesListConsumer =
    ParcelablePathAttributesListConsumer(this)