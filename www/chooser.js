/**
 * 导出方法
 */
module.exports = {
    getFile: function (accept, cb, w, h) {
        return new Promise(function (resolve, reject) {
            cordova.exec(
                function (json, bs) {
                    if (json === 'RESULT_CANCELED') {
                        resolve();
                        cb();
                        return;
                    }
                    try {
                        cb(json, bs);
                    }
                    catch (err) {
                        reject(err);
                    }
                },
                function (res) {
                    cb(false);
                },
                'Chooser',
                'getFile',
                [(typeof accept === 'string' ? accept.replace(/\s/g, '') : undefined) || '*/*', w, h]
            );
        });
    }
};
