package com.example.bluetooth_project.ui.base


import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel


abstract class BaseActivity<VM : ViewModel?> : AppCompatActivity() {

    var isLtr = true
    protected abstract val mViewModel: VM

}