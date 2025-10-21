/**
 * elem 에 datepicker 를 출력한다
 * @param {*} elem 
 * @returns 
 */
const showDatepicker = function (elem) {
	var datepicker = new Datepicker(elem, {
		buttonClass: 'btn',
		format: 'yyyy.mm.dd',
		autohide: true,
		weekStart: 1,
		startDate: new Date(),
		endDate: new Date(new Date().setDate(new Date().getDate() + 30)),
		language: 'ko'
	});
	return datepicker;
};

/**
* YYYYMMDD to YYYY.MM.DD
*/
const addDatedot = function (d) {
	let edate = d + "";
	let edate_str = "";
	edate_str += edate.substring(0, 4) + ".";
	edate_str += edate.substring(4, 6) + ".";
	edate_str += edate.substring(6);
	return edate_str;
};

/**
* YYYY.MM.DD to YYYYMMDD
*/
const delDatedot = function (d) {
	return d.replaceAll(".", "");
};

/**
 * 사용자 파라미터를 GET 요청으로 전송한다
 * @param {*} _ajax : XMLHttpRequest
 * @param {*} path : service url
 * @returns 
 */
const sendGet = function (_ajax, path) {
    _ajax.open("GET", path, true);
    _ajax.setRequestHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    try {
        _ajax.send();
    } catch (e) {
        if (e.message && e.message.indexOf("0x80004005") > -1) {
            return;
        }
    }
};

/**
 * urlencoded 된 data 를 전송한다
 * @param {*} _ajax : XMLHttpRequest
 * @param {*} path : service url
 * @param {*} params : &key=value
 * @returns 
 */
const sendPost = function (_ajax, path, params) {
    _ajax.open("POST", path, true);
    _ajax.setRequestHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    try {
        _ajax.send(params);
    } catch (e) {
        console.error(e.message);
        if (e.message && e.message.indexOf("0x80004005") > -1) {
            return;
        }
    }
};

/**
 * json 문자열 본문으로 변환하여 전송한다
 * @param {*} _ajax : XMLHttpRequest 
 * @param {*} path : service url
 * @param {*} params : Json {key:val}
 * @returns 
 */
const sendJson = function (_ajax, path, params) {
    _ajax.open("POST", path, true);
    _ajax.setRequestHeader("Content-Type", "application/json; charset=UTF-8");
    try {
        let jsonData = JSON.stringify(params); // JSON 문자열로 변환
        _ajax.send(jsonData);
    } catch (e) {
        console.error(e.message);
        if (e.message && e.message.indexOf("0x80004005") > -1) {
            return;
        }
    }
}

/**
 * 사용자 파라미터와 함께 file object 를 포함하여 multipart FormData 를 전송한다
 * @param {*} _ajax : XMLHttpRequest
 * @param {*} path : service url
 * @param {*} filesObj 
 * @param {*} params : Json Array([{name:"key", value:"val"}])
 * @returns 
 */
const sendMultipart = function (_ajax, path, filesObj, params) {    

    if (filesObj) {

        _ajax.open("POST", path, true);
        //Cotent-Type 값은 설정하지 않는다. (직접 설정시  적절한 boundary 값 추가가 필요)
        
        try {
            const promises = [];
            const formData = new FormData();

            for (const fObj of filesObj) {
                promises.push(new Promise((resolve, reject) => {
                    const fileReader = new FileReader();
            
                    fileReader.readAsDataURL(fObj);
                    //fileReader.readAsBinaryString(fObj);
            
                    fileReader.onload = function () {
                        formData.append("file", fObj);
                        resolve(fileReader.result);
                    };
            
                    fileReader.onerror = function () {
                        reject(fileReader.error);
                    };
                }));
            }

            Promise.all(promises).then((results) => {
                console.log(results);
                for (let p of params) {
                    //console.log(p.name + "-" + p.value);
                    formData.append(p.name, p.value);
                }
                _ajax.send(formData);
            });

        } catch (e) {
            console.error(e.message);
            if (e.message && e.message.indexOf("0x80004005") > -1) {
                return;
            }
        }

    } else {

        /**
         * files object 가 없는 경우 일반 post 로 전송
         */
        let getparams = '';
        params.forEach((param, index) => {
            if (index > 0) {
                getparams += '&';
            }
            getparams += param.name + '=' + param.value;
        });
        sendPost(_ajax, path, getparams);

    }
};

/**
 * ajax 통신 결과를 검사한다
 * @param {*} ajax 
 * @returns 
 */
const checkAjaxSuc = function (ajax) {
    if (ajax.readyState === XMLHttpRequest.DONE) { // XMLHttpRequest.DONE = 4
        try {
            return (ajax.status >= 200 && ajax.status < 300);
        } catch (ignore) {
            return false;
        }
    }
    return false;
}

/**
 * 요청 주소에 있는 파라미터를 조회하여 값을 반환한다
 * @param {*} name 
 * @returns 
 */
const urlparam = function (name) {
    const queryString = window.location.search;
    const urlParams = new URLSearchParams(queryString);
    if (!urlParams.has(name)) { return null; }
    return urlParams.get(name);
}

/**
 * input element 의 사용자 입력값을 전체 선택후 클립보드에 복사한다
 * @param {*} input 
 */
const copyToClipboard = function (input) {
    window.document.querySelector("#"+input).select();
    window.document.execCommand("copy");
    alert("링크를 복사하였습니다. 필요한 곳에 붙여넣기 해주세요.");
}

/**
 * 장보고에 등록된 쇼핑몰의 주문 내역을 조회
 */
const fetchShopOrders = function () {
    var path = "/jbs/jbg";
    var params = "cmd=getInfo";

    var _ajax = new XMLHttpRequest();
    _ajax.onreadystatechange = function () {
        if (checkAjaxSuc(_ajax)) {
            var resMsg = _ajax.responseText;
            if (resMsg.length > 0) {
                let resJo = JSON.parse(resMsg);
                if (resJo.code == '000') {
                    if (resJo.malls) {
                        var pathRe = "/jbs/jbg";

                        let malls = resJo.malls;
                        Object.keys(malls).forEach(key => {
                            var _ajaxRe = new XMLHttpRequest();

                            let mall = malls[key];
                            let mallId = localStorage.getItem(mall.cipidkey);
                            let mallPw = localStorage.getItem(mall.cippwkey);

                            //장보고에 요청
                            if (mallId && mallPw) {
                                let paramsRe = "cmd=fetchOrders";
                                paramsRe += "&seqm=" + mall.seq;
                                paramsRe += "&id=" + mallId;
                                paramsRe += "&pw=" + mallPw;
                                sendPost(_ajaxRe, pathRe, paramsRe);
                            } else if (mall.status == 1) {
                                let jbs_jbg_notnow = localStorage.getItem("jbs_jbg_notnow");
                                if (!jbs_jbg_notnow && confirm("현재 브라우저에서는 장보고에서 설정한 쇼핑몰 계정 정보가 없습니다.\n쇼핑몰 구매내역 자동 수집을 위해 장보고를 설정 하시겠습니까?")) {
                                    window.location.href = "/jbs/store_jbg.jsp"
                                } else {
                                    localStorage.setItem("jbs_jbg_notnow", true);
                                }
                            }

                        });
                    }
                } else if (resJo.msg) {
                    alert(resJo.msg);
                }
            }
        }
    };
    sendPost(_ajax, path, params);
}

/**
 * 버튼 요소에 spinner 를 덧붙여서 출력한다
 * @param {spinner로 전환할 버튼 요소} btn 
 */
const attachSpinnerButton = function(btn) {
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = "<span class='spinner-border spinner-border-sm' aria-hidden='true'></span>";
    }
}
/**
 * 버튼 요소의 spinner 를 제거하여 출력한다
 * @param {spinner를 제거할 버튼 요소} btn 
 * @param {출력할 문자} txt 
 */
const removeSpinnerButton = function(btn, txt) {
    if (btn) {
        btn.disabled = false;
        btn.innerHTML = txt ? txt : "";
    }
}
/**
 * div 요소에 spinner 를 덧붙여서 출력한다
 * @param {spinner를 출력할 div 요소} btn 
 */
const attachSpinnerDiv = function(div, txt) {
    if (div) {
        const _doc = window.document;

        var loading_bar = _doc.createElement("div");
        loading_bar.className = "d-flex align-items-center text-secondary";

        var loading_text = _doc.createElement("strong");
        loading_text.setAttribute("role", "status");
        loading_text.innerText = "Loading..." + (txt ? " "+txt : "");
        loading_bar.appendChild(loading_text);

        var spinner = _doc.createElement("div");
        spinner.className = "spinner-grow spinner-grow-sm ms-auto";
        spinner.setAttribute("aria-hidden", "true");
        loading_bar.appendChild(spinner);

        div.appendChild(loading_bar);
    }
}
/**
 * div 요소의 spinner 를 제거한다
 * @param {spinner를 제거할 div 요소} div 
 */
const removeSpinnerDiv = function(div) {
    if (div) {
        div.innerHTML = "";
    }
}

/**
 * hidden 과 display 속성을 한꺼번에 설정하기 위한 함수
 * @param {*} elem 
 */
const showElement = function(elem) {
    if (elem) {
        elem.hidden = false; elem.style.display = "block";
    }
}
const hideElement = function(elem) {
    if (elem) {
        elem.hidden = true; elem.style.display = "none";
    }
}

/**
 * 3초동안 깜박임
 * @param {*} elem 
 */
const highlight = function(elem) {
    var blinkDuration = 3000; // 3 seconds
    var intervalDuration = 500; // Blink interval duration

    var blinkInterval = setInterval(function() {
        elem.classList.toggle('blink-border');
        elem.classList.toggle('blink-bg');
    }, intervalDuration);

    setTimeout(function() {
        clearInterval(blinkInterval);
        elem.classList.remove('blink-border');
        elem.classList.remove('blink-bg');
    }, blinkDuration);
}

/**
 * iOS의 브라우저인지 확인
 * @returns iOS 디바이스 여부
 */
function isIOSDevice() {
    // 사용자 에이전트 문자열을 가져옵니다.
    var userAgent = navigator.userAgent || navigator.vendor || window.opera;

    // iOS 장치인지 확인합니다.
    if (/iPad|iPhone|iPod/.test(userAgent) && !window.MSStream) {
        return true;
    } else {
        return false;
    }
}

/**
 * 입력값이 숫자인지 여부
 * @returns true or false
 */
function isNumber(value) {
    // parseFloat를 사용하여 숫자로 변환 후 isNaN으로 체크
    return !isNaN(parseFloat(value)) && isFinite(value);
}

/**
 * 온보딩용 js를 로드하고 실행
 */
const userOnboarding = function(jsfile) {
    var chatbotModal = bootstrap.Modal.getOrCreateInstance(document.querySelector('#chatbotModal'));
    chatbotModal.hide();

    loadAndExecute(decodeURIComponent(jsfile), "userstep", ["arg1", "arg2"])
    .then((result) => {
        console.log("Function executed successfully. Result:", result);
    })
    .catch((err) => {
        console.error("Error:", err.message);
    });
}

/**
 * Dynamically load a JavaScript file and execute a function from it.
 * @param {string} fileUrl - The URL of the JavaScript file.
 * @param {string} functionName - The name of the function to execute.
 * @param {Array} args - Arguments to pass to the function.
 */
function loadAndExecute(fileUrl, functionName, args = []) {
    return new Promise((resolve, reject) => {
        // Create a script element
        const script = document.createElement("script");
        script.src = fileUrl;
        script.async = true;

        // On successful load
        script.onload = () => {
            try {
                // Ensure the function exists
                if (typeof window[functionName] === "function") {
                    // Call the function with provided arguments
                    const result = window[functionName](...args);
                    resolve(result);
                } else {
                    reject(new Error(`Function "${functionName}" not found in ${fileUrl}`));
                }
            } catch (err) {
                reject(err);
            }
        };

        // On load error
        script.onerror = () => {
            reject(new Error(`Failed to load script: ${fileUrl}`));
        };

        // Append the script to the document
        document.head.appendChild(script);
    });
}

