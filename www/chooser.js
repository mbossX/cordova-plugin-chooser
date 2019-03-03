cordova.define("cordova-plugin-chooser.Chooser", function (require, exports, module) {
    module.exports = {
        getFile: function (accept, cb) {
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
                    reject,
                    'Chooser',
                    'getFile',
                    [(typeof accept === 'string' ? accept.replace(/\s/g, '') : undefined) || '*/*']
                );
            });
        }
    };
});
