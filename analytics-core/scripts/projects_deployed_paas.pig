IMPORT 'macros.pig';

f1 = loadResources('$log');
f2 = filterByDate(f1, '$FROM_DATE', '$TO_DATE');
fR = filterByEvent(f2, 'application-created');

t1 = extractWs(fR);
t2 = extractUser(t1);
t3 = extractParam(t2, 'TYPE', 'type');
t4 = extractParam(t3, 'PROJECT', 'project');
tR = extractParam(t4, 'PAAS', 'paas');

result = FOREACH tR GENERATE TOTUPLE(TOTUPLE(ws), TOTUPLE(user), TOTUPLE(project), TOTUPLE(type), TOTUPLE(paas));

